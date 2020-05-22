import com.sun.mail.util.MailSSLSocketFactory;

import javax.mail.*;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.security.GeneralSecurityException;
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

    static {
        //加载配置文件
        Properties properties = new Properties();
        InputStream in = null;
        try {
            String configPath=new File(ToEmail.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent()+"/config.properties";
            configPath=URLDecoder.decode(configPath, "utf-8");
            in = new FileInputStream(configPath);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        try {
            properties.load(new InputStreamReader(in, "utf-8"));
            ToEmail.fromEmailAddress = properties.getProperty("fromEmailAddress").trim();
            ToEmail.toEmailAddress = properties.getProperty("toEmailAddress").trim();
            ToEmail.server = properties.getProperty("server").trim();
            ToEmail.AuthorizationCode = properties.getProperty("AuthorizationCode").trim();
            ToEmail.title = properties.getProperty("title").trim();
            ToEmail.content = properties.getProperty("content").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Throwable {
        //等待网络通畅
        ToEmail.waitToPass();

        //增加附加内容
        String dateString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String localIp = ToEmail.getLocalHostLANAddress().getHostAddress();
        String hostName = Inet4Address.getLocalHost().getHostName();
        String publicIp = null;
        try {
            publicIp = ToEmail.getPublicIp();
            if (publicIp.trim().equals("")) {
                publicIp = "未获取到值";
            }
        } catch (Throwable e) {
            System.out.println("公网ip获取失败");
        }

        String extraInformation = "<br><br><br>----------------------------------------------------------<br>" + "来自外网ip为 “" + publicIp + "”<br>内网ip为 “" + localIp + "”<br>主机名称为 “" + hostName + "”的计算机的信息<br><br>发送时间为 “" + dateString + "”";
        new ToEmail().sendEmail(ToEmail.title, ToEmail.content + extraInformation);
        System.out.println("邮件发送成功");

    }

    //等待到网络通畅
    public static void waitToPass() throws InterruptedException {
        while (true) {
            try {
                InetAddress.getByName("www.baidu.com").isReachable(5000);
                break;
            } catch (IOException e) {
                System.out.println("请连接网络...");
                Thread.sleep(60 * 1000);
            }
        }
    }

    //获取公网ip
    public static String getPublicIp() {
        String ip = "";
        String chinaz = "http://ip.chinaz.com";

        StringBuilder inputLine = new StringBuilder();
        String read = "";
        URL url = null;
        HttpURLConnection urlConnection = null;
        BufferedReader in = null;
        try {
            url = new URL(chinaz);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));
            while ((read = in.readLine()) != null) {
                inputLine.append(read + "\r\n");
            }
            //System.out.println(inputLine.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


        Pattern p = Pattern.compile("\\<dd class\\=\"fz24\">(.*?)\\<\\/dd>");
        Matcher m = p.matcher(inputLine.toString());
        if (m.find()) {
            String ipstr = m.group(1);
            ip = ipstr;
            //System.out.println(ipstr);
        }
        return ip;
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
    public boolean sendEmail(String title, String content) throws MessagingException, GeneralSecurityException {
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
        } catch (Throwable e) {
            System.out.println("无法正确连接到邮件服务器，请检查邮箱、授权码及邮箱服务器地址是否填写正确");
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
        transport.sendMessage(mimeMessage, mimeMessage.getAllRecipients());

        //关闭连接
        transport.close();
        return true;
    }
}