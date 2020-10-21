package cn.freshz.javasteam.controller;

import cn.freshz.javasteam.util.ThreadPoolUtil;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.networking.steam3.ProtocolTypes;
import in.dragonbra.javasteam.steam.discovery.ServerRecord;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.VACStatusCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.MachineAuthDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.OTPDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.UpdateMachineAuthCallback;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration;
import in.dragonbra.javasteam.steam.steamclient.configuration.SteamConfigurationBuilder;
import in.dragonbra.javasteam.steam.steamclient.configuration.SteamConfigurationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Controller
@ResponseBody
@Scope(WebApplicationContext.SCOPE_REQUEST)
@RequestMapping("steam/api")
public class SteamApi {
    private Logger logger= LoggerFactory.getLogger(this.getClass());

    private SteamClient steamClient;
    private SteamUser steamUser;
    private String user;
    private String pass;

    private String connectUrl="cm3-ct-sha2.cm.wmsjsteam.com:27020";

    private final static String SUCCESS="SUCCESS";
    private final static String ERROR_RETRY = "登陆有异常,请重试";

    private volatile boolean isRuning=false;
//    private Boolean isLogined=null;

    private volatile EResult loginEResult;

    private volatile boolean isSendachineAuth=false;


    private long connectStartTime;

    @GetMapping("getSteamID")
    public String getSteamID(){
        String checkRet = checkArg();
        if (!SUCCESS.equals(checkRet)) {
            return "";
        }

        if (steamClient.isConnected()&&steamClient.getSteamID()!=null) {
            logger.error("steamClient获取成功,account:{},steamID:{}",user,steamClient.getSteamID().convertToUInt64());

            return steamClient.getSteamID().convertToUInt64()+"";
        }else{
            logger.error("steamClient获取失败,account:{}",user);
            return "";
        }
    }

    /**
     * 关闭令牌的验证链接如下，如果域名前缀是 dota2  需要替换
     * https://store.steampowered.com/account/steamguarddisableverification/actions/steamguarddisableverification?stoken=773c8bb536e151f0ec43d2d3d15772d0a03527a16fccdb78fff2800f91483d3203c6e26d14bf714ab295775431a71d1e&steamid=76561199079639627
     *
     * 检测令牌接口返回值列表         AccountLogonDenied  要去关令牌 | OK 表示 不用去关令牌 | 登陆有异常,请重试 | 其它就是号有问题
     * @return
     */
    @GetMapping("checkSteamGuard")
    public String checkSteamGuard(){
        String checkRet = checkArg();
        if (!SUCCESS.equals(checkRet)) {
            return checkRet;
        }

        while (null==loginEResult){
            return ERROR_RETRY;
        }
        if(loginEResult!=EResult.OK){
            logger.info("帐号状态:account:{} -->{}",user,loginEResult.name());
            return loginEResult.name();
        }
        if(!isSendachineAuth){
            synchronized (steamUser){
                try {
                    steamUser.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        steamUser.logOff();
        steamClient.disconnect();
        try {
            Thread.sleep(100L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //重新登陆验证结果
        login();
        if(null==loginEResult){
            synchronized (steamClient){
                try {
                    steamClient.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        String retVal = null == loginEResult ? ERROR_RETRY : loginEResult.name();
        logger.info("帐号状态account:{} -->{}",user,retVal);

        return retVal;
    }
//    @GetMapping("activeSteamGuard")
    public String activeSteamGuard(){
        String checkRet = checkArg();
        if (!SUCCESS.equals(checkRet)) {
            return checkRet;
        }
        if (null!=loginEResult&&loginEResult==EResult.OK) {
            steamUser.activeSteamGuard();
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return SUCCESS;
        }else{
            return "";
        }
    }

    private String checkArg(){
        if(StringUtils.isEmpty(user)){
            logger.warn("user参数为空");
            return "user参数为空";
        }
        if(StringUtils.isEmpty(pass)){
            logger.warn("pass参数为空");
            return "pass参数为空";
        }

        if(null==steamClient){
            logger.error("steamClient获取异常,account:{}",user);
            return "steamClient获取异常";
        }

        if(null==loginEResult){
            synchronized (steamClient){
                try {
                    steamClient.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return SUCCESS;
    }

    @PreDestroy
    private void destoru() {
        if (steamClient.isConnected()){
            steamUser.logOff();
        }
    }

    @PostConstruct
    private void login(){
        loginEResult=null;
        isRuning=false;
        isSendachineAuth=false;
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

        manager.subscribe(UpdateMachineAuthCallback.class,this::onUpdateMachineAuthCallback);

//        manager.subscribe(VACStatusCallback.class,this::onVACStatus);

        // initiate the connection
        connectStartTime=System.currentTimeMillis();
        logger.info("start connect Steam server,account:{}",user);

        steamClient.connect(ServerRecord.createWebSocketServer(connectUrl));

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
            synchronized (steamUser) {
                steamUser.notify();
            }
            steamClient.disconnect();
        });
    }

    private void onUpdateMachineAuthCallback(UpdateMachineAuthCallback updateMachineAuthCallback){
        try {
            OTPDetails otp = new OTPDetails();
            otp.setIdentifier(updateMachineAuthCallback.getOneTimePassword().getIdentifier());
            otp.setType(updateMachineAuthCallback.getOneTimePassword().getType());
            otp.setValue(41);

            MachineAuthDetails details = new MachineAuthDetails();

            details.setJobID(updateMachineAuthCallback.getJobID());
            details.setFileName(updateMachineAuthCallback.getFileName());
            details.setBytesWritten(updateMachineAuthCallback.getBytesToWrite());
            details.setFileSize(16);
            details.setOffset(updateMachineAuthCallback.getOffset());
            details.seteResult(EResult.OK);
            details.setLastError(1);
            details.setOneTimePassword(otp);
            details.setSentryFileHash(updateMachineAuthCallback.getData());

            steamUser.sendMachineAuthResponse(details);

            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            isSendachineAuth=true;
        } finally {
            synchronized (steamUser) {
                steamUser.notify();
            }
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
        //like dota2
        details.setLauncherType(1);
        steamUser.logOn(details);
    }

    private void onDisconnected(DisconnectedCallback callback) {
        isRuning=false;
        synchronized (steamClient) {
            steamClient.notify();
            logger.info("Disconnected from Steam,acc:" + user + ", isUserInitiated:" + callback.isUserInitiated());
        }
        synchronized (steamUser) {
            steamUser.notify();
        }
    }

    private void onLoggedOn(LoggedOnCallback callback) {
        synchronized (steamClient){
            try {
                loginEResult=callback.getResult();
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
