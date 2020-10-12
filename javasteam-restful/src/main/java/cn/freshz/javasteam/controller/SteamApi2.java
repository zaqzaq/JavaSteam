package cn.freshz.javasteam.controller;

import cn.freshz.javasteam.util.ThreadPoolUtil;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.enums.EUniverse;
import in.dragonbra.javasteam.networking.steam3.ProtocolTypes;
import in.dragonbra.javasteam.steam.discovery.ServerRecord;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.VACStatusCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoginKeyCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.WebAPIUserNonceCallback;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration;
import in.dragonbra.javasteam.util.KeyDictionary;
import in.dragonbra.javasteam.util.WebHelpers;
import in.dragonbra.javasteam.util.crypto.CryptoHelper;
import in.dragonbra.javasteam.util.crypto.RSACrypto;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;

@Controller
@ResponseBody
@Scope(WebApplicationContext.SCOPE_REQUEST)
@RequestMapping("steam/api2")
public class SteamApi2 {

    private Logger logger= LoggerFactory.getLogger(this.getClass());

    private SteamClient steamClient;
    private CallbackManager manager;
    private SteamUser steamUser;
    private String user;
    private String pass;

    private boolean isRuning=false;

    private long connectStartTime;

    private RestTemplate restTemplate=new RestTemplate();
    {
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler(){
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });
    }

    @GetMapping("getSteamID")
    public String getSteamID(){

        if(StringUtils.isEmpty(user)){
            logger.warn("user参数为空");
            return "";
        }
        if(StringUtils.isEmpty(pass)){
            logger.warn("pass参数为空");
            return "";
        }

        if(null==steamClient){
            logger.error("steamClient获取异常,account:{}",user);
            return "";
        }

        synchronized (steamClient){
            try {
                steamClient.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (steamClient.isConnected()&&steamClient.getSteamID()!=null) {
            logger.error("steamClient获取成功,account:{},steamID:{}",user,steamClient.getSteamID().convertToUInt64());

            return steamClient.getSteamID().convertToUInt64()+"";
        }else{
            logger.error("steamClient获取失败,account:{}",user);
            return "";
        }
    }

    @GetMapping("getSteamLoginWebToken")
    public String getSteamLoginWebToken(){
        return null;
    }

    @PreDestroy
    private void destoru() {
        if (steamClient.isConnected()){
           // steamUser.logOff();
        }
    }

    @PostConstruct
    private void login(){
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
        this.user= requestAttributes.getRequest().getParameter("user");
        this.pass= requestAttributes.getRequest().getParameter("pass");

        // create our steamclient instance
        SteamConfiguration steamConfiguration=SteamConfiguration.create(iSteamConfigurationBuilder -> {
            iSteamConfigurationBuilder.withProtocolTypes(ProtocolTypes.ALL);
        });
        steamClient = new SteamClient(steamConfiguration);

        // create the callback manager which will route callbacks to function calls
        CallbackManager manager = new CallbackManager(steamClient);

        // get the steamuser handler, which is used for logging on after successfully connecting
        steamUser = steamClient.getHandler(SteamUser.class);
        // register a few callbacks we're interested in
        // these are registered upon creation to a callback manager, which will then route the callbacks
        // to the functions specified
        manager.subscribe(ConnectedCallback.class, this::onConnected);
        manager.subscribe(DisconnectedCallback.class, this::onDisconnected);

        manager.subscribe(LoggedOnCallback.class, this::onLoggedOn);
        manager.subscribe(LoggedOffCallback.class, this::onLoggedOff);

        manager.subscribe(LoginKeyCallback.class, this::onLoginKeyCallback);


        manager.subscribe(WebAPIUserNonceCallback.class,this::onWebAPIUserNonceCallback);
//        manager.subscribe(VACStatusCallback.class,this::onVACStatus);
        // initiate the connection
        connectStartTime=System.currentTimeMillis();
        logger.info("start connect Steam server,account:{}",user);

        steamClient.connect(ServerRecord.createWebSocketServer("cm3-ct-sha2.cm.wmsjsteam.com:27020"));
//        /203.80.149.68:27017
//        steamClient.getServers().getNextServerCandidate()
//        steamClient.connect();

        isRuning=true;
        ThreadPoolUtil.async(() -> {
            while (isRuning&&System.currentTimeMillis()-connectStartTime<=1000*30) {
                // in order for the callbacks to get routed, they need to be handled by the manager
                manager.runWaitCallbacks(2000L);
            }
            synchronized (steamClient) {
                steamClient.notify();
            }
            steamClient.disconnect();
        });
    }


    private static String LoginKey=null;

    private void onLoginKeyCallback(LoginKeyCallback callback) {
        System.out.println("onLoginKeyCallback:"+callback.getLoginKey());
        LoginKey=callback.getLoginKey();
        steamUser.requestWebAPIUserNonce();

    }


    private void onWebAPIUserNonceCallback(WebAPIUserNonceCallback callback) {
        System.out.println(callback.getNonce());
        System.out.println(callback);

        while (LoginKey==null){
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {

            byte[] sessionKey  = CryptoHelper.generateRandomBlock(32);

            RSACrypto rsa = new RSACrypto(KeyDictionary.getPublicKey(EUniverse.Public));

            byte[] cryptedSessionKey = rsa.encrypt(sessionKey);

            String nonce = callback.getNonce();

            // aes encrypt the loginkey with our session key
            byte[] cryptedLoginKey = CryptoHelper.symmetricEncrypt(nonce.getBytes(), sessionKey);

            HttpClientBuilder custom = HttpClients.custom();
            CloseableHttpClient httpClient = custom.build();

           HttpPost httpPost=new HttpPost("https://api.steampowered.com/ISteamUserAuth/AuthenticateUser/v0001");

            StringBuilder args=new StringBuilder();
            args.append("format=vdf&steamid="+steamClient.getSteamID().convertToUInt64());
            args.append("&sessionkey="+WebHelpers.urlEncode(cryptedSessionKey));
            args.append("&encrypted_loginkey="+WebHelpers.urlEncode(cryptedLoginKey));

            StringEntity urlEncodedFormEntity=new StringEntity(args.toString(), ContentType.APPLICATION_FORM_URLENCODED);
            httpPost.setEntity(urlEncodedFormEntity);

            CloseableHttpResponse httpResponse = httpClient.execute(httpPost);

            System.out.println(EntityUtils.toString(httpResponse.getEntity()));




        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onVACStatus(VACStatusCallback callback){
        if (callback.getBannedApps().size()>0) {
            logger.info(user+" has VAC: "+callback.getBannedApps());
        }else{
            logger.info(user+" has`t VAC");
        }
    }

    private void onConnected(ConnectedCallback callback) {
        logger.info("Connected to Steam! Logging in " + user + "...");

        LogOnDetails details = new LogOnDetails();
        details.setUsername(user);
        details.setPassword(pass);

        steamUser.logOn(details);
    }

    private void onDisconnected(DisconnectedCallback callback) {
        isRuning=false;
        synchronized (steamClient) {
            steamClient.notify();
            logger.info("Disconnected from Steam,acc:" + user + ", isUserInitiated:" + callback.isUserInitiated());
        }
    }

    private void onLoggedOn(LoggedOnCallback callback) {
        synchronized (steamClient){
            try {
                if (callback.getResult() != EResult.OK) {
                    if (callback.getResult() == EResult.AccountLogonDenied) {
                        // if we recieve AccountLogonDenied or one of it's flavors (AccountLogonDeniedNoMailSent, etc)
                        // then the account we're logging into is SteamGuard protected
                        // see sample 5 for how SteamGuard can be handled
                        logger.info("Unable to logon to Steam: This account is SteamGuard protected.");
                        return;
                    } else if (callback.getResult() == EResult.InvalidPassword) {

                    } else if (callback.getResult() == EResult.RateLimitExceeded) {
                        logger.info(" WARN to change IP!!!!!!!!!!!!!!!");
                        logger.info(" WARN to change IP");
                        logger.info(" WARN to change IP!!!!!!!!!!!!!!!");
                    } else if (callback.getResult() == EResult.RateLimitExceeded) {

                    } else if (callback.getResult() == EResult.AccountLoginDeniedNeedTwoFactor) {

                    }

                    logger.info("Unable to logon to Steam: " + callback.getResult());
                    return;

                }

                logger.info("Successfully logged on!");
            }finally {
                steamClient.notify();
            }
        }
    }

    private void onLoggedOff(LoggedOffCallback callback) {
        logger.info("Logged off of Steam: " + callback.getResult());
    }

}
