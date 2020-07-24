import com.sun.mail.util.MailSSLSocketFactory;

import javax.mail.*;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ToEmail {
    private static String fromEmailAddress; //发送邮箱
    private static String toEmailAddress;   //接收邮箱
    private static String server;   //邮件服务器
    private static String AuthorizationCode;    //服务器给你的授权码
    private static String title;    //邮件标题
    private static String content;    //邮件文字内容
    private static String lastSendTime; //上次登录时间 用于实现一段时间内只发一次邮件功能
    private static String interval; //多次发送最短时间间隔（单位为秒） 用于实现一段时间内只发一次邮件功能

    private static String info;


    static {
        //加载配置文件（放在最终jar包外面）
        Properties properties = new Properties();
        Reader in = null;
        try {
            String configPath = new File(ToEmail.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() + "\\config.properties";
            configPath = URLDecoder.decode(configPath, "utf-8");
            in = new InputStreamReader(new FileInputStream(configPath), "utf-8");
        } catch (Throwable e) {
            e.printStackTrace();
        }
        try {
            properties.load(in);
            ToEmail.fromEmailAddress = properties.getProperty("fromEmailAddress").trim();
            ToEmail.toEmailAddress = properties.getProperty("toEmailAddress").trim();
            ToEmail.server = properties.getProperty("server").trim();
            ToEmail.AuthorizationCode = properties.getProperty("authorizationCode").trim();
            ToEmail.title = properties.getProperty("title").trim();
            ToEmail.content = properties.getProperty("content").trim();
            ToEmail.interval = properties.getProperty("interval").trim();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {

        //如果若干时间间隔（interval）前已经发过则不再发送
        boolean isAlreadySent = ToEmail.alreadySent(Integer.parseInt(ToEmail.interval));
        if (isAlreadySent) {
            ToEmail.writeToLog("已经发送过了，请等待" + ToEmail.interval + "秒后再重新发送");
            return;
        }

        String dateString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        ToEmail.waitToPass();   //等待网络通畅
        try {
            info = ToEmail.getInfo();
        } catch (Exception e) {
            System.out.println("附加消息获取失败");
            ToEmail.writeToLog("附加消息获取失败");
            e.printStackTrace();
        }
        String localIp = ToEmail.getLocalHostLANAddress().getHostAddress();
        String hostName = Inet4Address.getLocalHost().getHostName();
        String publicIp = ToEmail.getPublicIp().equals("") ? "未获取到值" : ToEmail.getPublicIp();
        String networkServiceProvider = ToEmail.getNetwordServiceProvider().equals("") ? "未获取到值" : ToEmail.getNetwordServiceProvider();
        String tencentPosition = ToEmail.getTencentPosition().equals("") ? "未获取到值" : ToEmail.getTencentPosition();
        String OSName = ToEmail.getOSName().equals("") ? "未获取到值" : ToEmail.getOSName();

        //增加附加内容
        String extraInformation =
                "<br><br><br>----------------------------------------------------------<br>"
                        + "发生时间为 “"
                        + dateString + "”"
                        + "<br><br>来自外网ip为 “"
                        + publicIp
                        + "（" + ToEmail.getNetwordServiceProvider()
                        + "）”<br>内网ip为 “"
                        + localIp
                        + "”<br>操作系统为“"
                        + OSName
                        + "”<br>腾讯定位为“"
                        + tencentPosition
                        + "”<br>主机名称为 “"
                        + hostName
                        + "”的计算机的信息";

        //发送邮件
        new ToEmail().sendEmail(ToEmail.title, ToEmail.content + extraInformation);
        System.out.println("邮件发送成功");
        ToEmail.writeToLog("邮件发送成功");

    }

    //等待到网络通畅
    public static void waitToPass() throws InterruptedException {
        while (true) {
            try {
                InetAddress.getByName("www.baidu.com").isReachable(5000);
                break;
            } catch (Exception e) {
                String exceptionMsg = "网络未连接，1分钟后重试...";
                System.out.println(exceptionMsg);
                ToEmail.writeToLog(exceptionMsg);
                Thread.sleep(60 * 1000);
            }
        }
    }


    //获取公网ip
    public static String getPublicIp() {
        if (info == null) {
            return "";
        }
        String[] infos = info.split("\t");
        for (String result : infos) {
            if (result.contains("publicIp")) {
                return result.replace("publicIp=", "");
            }
        }
        return "";
    }

    //获取网络服务商
    public static String getNetwordServiceProvider() {
        if (info == null) {
            return "";
        }
        String[] infos = info.split("\t");
        for (String result : infos) {
            if (result.contains("networkServiceProvider")) {
                return result.replace("networkServiceProvider=", "");
            }
        }
        return "";
    }

    //获取腾讯定位位置
    public static String getTencentPosition() {
        if (info == null) {
            return "";
        }
        String[] infos = info.split("\t");
        for (String result : infos) {
            if (result.contains("tencentPositon")) {
                return result.replace("tencentPositon=", "");
            }
        }
        return "";
    }

    //获取操作系统名称
    public static String getOSName() {
        if (info == null) {
            return "";
        }
        String[] infos = info.split("\t");
        for (String result : infos) {
            if (result.contains("OSName")) {
                return result.replace("OSName=", "");
            }
        }
        return "";
    }

    //从网站爬取信息
    private static String getInfo() throws Exception {
        String ip = "";
        String region = "";
        String tencentPositon = "";
        String OSName = "";

        String chinaz = "https://www.sojson.com/ip/";
        StringBuilder inputLine = new StringBuilder();
        String read = "";
        URL url = null;
        HttpURLConnection urlConnection = null;
        BufferedReader in = null;
        try {
            url = new URL(chinaz);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("user-agent", " Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36");
            in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));
            while ((read = in.readLine()) != null) {
                inputLine.append(read + "\r\n");
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


        Pattern p = Pattern.compile(">IP</th>\\s*.*>(.*?)</td>");
        Matcher m = p.matcher(inputLine.toString());
        if (m.find()) {
            ip = m.group(1);
        }

        p = Pattern.compile("SOJSON.COM 定位</th>\\s*.*>(.*?)</td>");
        m = p.matcher(inputLine.toString());
        if (m.find()) {
            region = m.group(1);
        }

        p = Pattern.compile(">腾讯定位</th>\\s*<td>\\s*(.*)\\s*.*?\\s*</td>");
        m = p.matcher(inputLine.toString());
        if (m.find()) {
            tencentPositon = m.group(1);
        }

        p = Pattern.compile(">操作系统</th>\\s*.*>(.*?)</td>");
        m = p.matcher(inputLine.toString());
        if (m.find()) {
            OSName = m.group(1);
        }


        return "publicIp=" + ip + "\t" + "networkServiceProvider=" + region + "\t" + "tencentPositon=" + tencentPositon + "\t" + "OSName=" + OSName;
    }

    //获取本地ip（排除虚拟机地址影响）
    private static InetAddress getLocalHostLANAddress() throws UnknownHostException {
        try {
            InetAddress candidateAddress = null;
            // 遍历所有的网络接口
            for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements(); ) {
                NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
                // 在所有的接口下再遍历IP
                for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements(); ) {
                    InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
                    if (!inetAddr.isLoopbackAddress()) {// 排除loopback类型地址
                        if (inetAddr.isSiteLocalAddress()) {
                            // 如果是site-local地址，就是它了
                            return inetAddr;
                        } else if (candidateAddress == null) {
                            // site-local类型的地址未被发现，先记录候选地址
                            candidateAddress = inetAddr;
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                return candidateAddress;
            }
            // 如果没有发现 non-loopback地址.只能用最次选的方案
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (jdkSuppliedAddress == null) {
                throw new UnknownHostException("The JDK InetAddress.getLocalHost() method unexpectedly returned null.");
            }
            return jdkSuppliedAddress;
        } catch (Exception e) {
            UnknownHostException unknownHostException = new UnknownHostException(
                    "Failed to determine LAN address: " + e);
            unknownHostException.initCause(e);
            throw unknownHostException;
        }
    }

    //发送邮件
    public boolean sendEmail(String title, String content) throws Exception {
        //创建一个配置文件并保存
        Properties properties = new Properties();

        properties.setProperty("mail.host", ToEmail.server);

        properties.setProperty("mail.transport.protocol", "smtp");

        properties.setProperty("mail.smtp.auth", "true");


        //QQ存在一个特性设置SSL加密
        MailSSLSocketFactory sf = new MailSSLSocketFactory();
        sf.setTrustAllHosts(true);
        properties.put("mail.smtp.ssl.enable", "true");
        properties.put("mail.smtp.ssl.socketFactory", sf);

        //创建一个session对象
        Session session = Session.getDefaultInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(ToEmail.fromEmailAddress, ToEmail.AuthorizationCode);
            }
        });

        //开启debug模式
//        session.setDebug(true);

        //获取连接对象
        Transport transport = session.getTransport();

        //连接服务器
        try {
            transport.connect(ToEmail.server, ToEmail.fromEmailAddress, ToEmail.AuthorizationCode);
        } catch (Exception e) {
            String exceptionMsg = "无法正确连接到邮件服务器，请检查邮箱、授权码及邮箱服务器地址是否填写正确";
            System.out.println(exceptionMsg);
            ToEmail.writeToLog(exceptionMsg);
            throw e;
        }
        //创建邮件对象
        MimeMessage mimeMessage = new MimeMessage(session);

        //邮件发送人
        mimeMessage.setFrom(new InternetAddress(ToEmail.fromEmailAddress));

        //邮件接收人
        mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(ToEmail.toEmailAddress));

        //邮件标题
        mimeMessage.setSubject(title);

        //邮件内容
        mimeMessage.setContent(content, "text/html;charset=UTF-8");

        //发送邮件
        try {
            transport.sendMessage(mimeMessage, mimeMessage.getAllRecipients());
        } catch (Exception e) {
            String exceptionMsg = "发送邮件失败（可能被当做垃圾邮件了）";
            System.out.println(exceptionMsg);
            ToEmail.writeToLog(exceptionMsg);
            throw e;
        }

        //关闭连接
        transport.close();
        return true;
    }

    //写日志
    private static void writeToLog(String exceptionMsg) {
        exceptionMsg = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\t\t" + exceptionMsg;
        String logPath = new File(ToEmail.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() + "\\log.txt";
        try {
            logPath = URLDecoder.decode(logPath, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        File logFile = new File(logPath);
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        BufferedReader in = null;
        BufferedWriter out = null;
        try {

            if (logFile.length() > 100 * 1024) {
                logFile.delete();
                try {
                    logFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            in = new BufferedReader(new InputStreamReader(new FileInputStream(logFile), "utf-8"));
            String tempString = null;
            StringBuilder originContent = new StringBuilder();

            for (int readNum = 0; (tempString = in.readLine()) != null && readNum < 399; readNum++) {
                originContent.append(tempString);
                originContent.append(System.getProperty("line.separator"));
            }
            //创建输出流会删除原文件创建同名新文件所以不要把这行放到前面否则会读不到原文件的内容
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile), "utf-8"));


            out.append(exceptionMsg + System.getProperty("line.separator") + originContent);

            in.close();
            out.close();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //判断若干时间段内是否发送过邮件
    private static boolean alreadySent(int interval) {
        try {
            String logPath = new File(ToEmail.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() + "\\log.txt";
            logPath = URLDecoder.decode(logPath, "utf-8");
            long lastSendTimeMillis = new File(logPath).lastModified(); //把日志上次的修改时间作为邮件上次发送时间
            if (System.currentTimeMillis() - lastSendTimeMillis > interval * 1000) {
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            ToEmail.writeToLog("上次发送时间获取失败");
            return false;
        }

    }
}
