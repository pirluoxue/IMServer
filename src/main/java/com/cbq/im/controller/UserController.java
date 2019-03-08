package com.cbq.im.controller;


import com.alibaba.fastjson.JSONArray;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.lingdu.manager.ApiAlipayManager;
import com.lingdu.model.*;
import com.lingdu.service.ApiAlipayOrderService;
import com.lingdu.service.Impl.UserServiceImpl;
import com.lingdu.service.SysConfigService;
import com.lingdu.service.UserService;
import com.lingdu.util.*;
import com.lingdu.vo.ChatMsg;
import com.lingdu.vo.FriendVo;
import com.lingdu.vo.GroupVo;
import com.lingdu.vo.UserVo;
import com.lingdu.wap.alipay.config.AlipayConfig;
import net.sf.json.JSONObject;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;


/**
 * Created with IntelliJ IDEA.
 * User: Administrator
 * Date: 17-10-29
 * Time: 上午1:53
 * To change this template use File | Settings | File Templates.
 */

@Controller
@RequestMapping(value = "/user")
public class UserController {
    private Logger log=Logger.getLogger(UserController.class);


    @Autowired
    UserService userService;
     @Autowired
    ApiAlipayOrderService apiAlipayOrderService;
    @Autowired
    SysConfigService sysConfigService;
    @Autowired
    ApiAlipayManager apiAlipayManager;

    @RequestMapping(value = "/enroll.htm" ,method = RequestMethod.POST)
    @ResponseBody
    public OpResult enroll(User user, String code, HttpServletRequest request){
        OpResult opResult=new OpResult(OpResult.OP_FAILED, OpResult.OpMsg.OP_FAIL);
        try {
            if(userService.selectUserLogin(user)!=null){
                opResult.setMessage("会员号已注册！");
                return   opResult;
            }
            //测试过滤验证码
            if("1234".equals(code)){
                //注册
                user.setUserPwd(XiaoLinUtil.MD5util.toMD5(user.getUserPwd(),null));    //加密
                user.setHeadImg("/static/imgs/head.png");
                user.setNickName(user.getUserNo());
                user.setBgImg("/static/chat/img/1.png");
                user.setCreateDate(new Date());
                if(userService.insert(user)>0){
                    return    new OpResult(OpResult.OP_SUCCESS, OpResult.OpMsg.OP_SUCCESS);
                }
                return opResult;
            }
            //验证验证码
            //存储验证码
            Object obj= SessionManage.getSession(user.getUserNo(),request);
            if(obj==null){
                opResult.setMessage("验证码已过期");
                return opResult;

            }
            if(!obj.toString().equals(code)){
                opResult.setMessage("验证码错误");
                return opResult;
            }
            //注册
            user.setUserPwd(XiaoLinUtil.MD5util.toMD5(user.getUserPwd(),null));    //加密

             user.setHeadImg("/static/imgs/head.png");
            user.setNickName(user.getUserNo());
             user.setBgImg("/static/chat/img/1.png");
            user.setCreateDate(new Date());
             if(userService.insert(user)>0){
                 return    new OpResult(OpResult.OP_SUCCESS, OpResult.OpMsg.OP_SUCCESS);
             }
        }catch (Exception e){
               log.error("注册失败！user="+ JSONObject.fromObject(user).toString());
        }
        return opResult;
    }
    @RequestMapping(value = "/login.htm" ,method = RequestMethod.POST)
    @ResponseBody
    public OpResult taskDetail(User user, HttpServletRequest request){
        OpResult opResult=new OpResult(OpResult.OP_FAILED,"密码错误！");
        //登陆验证
        String pwd=user.getUserPwd();
        user=userService.selectUserLogin(user) ;
        if(user==null){opResult.setMessage("账号不存在！");return opResult;}
        if(user.getUserPwd().equals(XiaoLinUtil.MD5util.toMD5(pwd,null))){
            //验证账号状态
            if( user.getUsable()== SysConstants.isOrNot.NO){
                opResult.setMessage("账号被冻结！");return opResult;
            }
            //写入登陆信息
            String userAddrValue = UUID.randomUUID().toString();
            SessionManage.setSession(SysConstants.USER_KEY, user, request, user.getId().toString(), userAddrValue);
            /*-------清除其他浏览器的登录信息------*/
            ChatController chatController = new ChatController();
            chatController.onClose(user.getId().toString());
            //服务器记录登录用户地址
            chatController.addUserAddr(user.getId().toString(), userAddrValue);
            /*-------清除其他浏览器的登录信息------*/
            return    new OpResult(OpResult.OP_SUCCESS, OpResult.OpMsg.OP_SUCCESS);
        }
        return opResult;
    }

    /**
     * QQ登录授权回调
     * @param code
     * @param request
     * @return
     */
    @RequestMapping(value = "/qq_login.htm" )
    public String qq_login(String code, String access_token, String expires_in, String refresh_token, HttpServletRequest request, HttpServletResponse response){

        try {
            //先获取accesstoken
            if(access_token==null ||"".equals(access_token)){
                String qq_url="https://graph.qq.com/oauth2.0/token";
                String domain=request.getScheme()+"://"+ request.getServerName();
                String redirect_uri=domain+"/user/qq_login.htm" ;

                String client_id=sysConfigService.getSysConfigByKey(SysConstants.Oauth.QQ_LOGIN_APPID);
                String client_secret=  sysConfigService.getSysConfigByKey(SysConstants.Oauth.QQ_LOGIN_SECRED);
               Object obj=  HttpClient.getObject(qq_url+"?grant_type=authorization_code&client_id="+client_id+"&client_secret="+client_secret+"&redirect_uri="+redirect_uri+"&code="+code) ;
               access_token=obj.toString().substring(13,obj.toString().indexOf("&"));
                log.info("----------------tokenz值是"+access_token);

                //获取openid 去登录
                 qq_url="https://graph.qq.com/oauth2.0/me";


               Object object= HttpClient.getObject(qq_url+"?access_token="+access_token) ;
                log.info(object.toString());
                JSONObject object1=JSONObject.fromObject(object.toString().substring(object.toString().indexOf("{"),object.toString().indexOf("}")+1))   ;

               String openId=object1.getString("openid");
                //qu登录
                User user=new User();
                user.setQqOpenid(openId);
                user= userService.selectUserLogin(user) ;
                if(user!=null){
                    SessionManage.setSession(SysConstants.USER_KEY,user,request);
                    return "/login";
                }
                //去创建或者绑定用户
                //已登录用户自动绑定
               User login_user= (User)SessionManage.getSession(SysConstants.USER_KEY,request);
                if(login_user!=null){
                    User up_user=new User();
                    up_user.setId(login_user.getId());
                    up_user.setQqOpenid(openId);
                   login_user.setQqOpenid(openId);
                    userService.updateByPrimaryKeySelective(up_user)  ;
                    SessionManage.setSession(SysConstants.USER_KEY,login_user,request);
                    return "/login";
                }
               SessionManage.setSession("qq_openid",openId,request);
                SessionManage.setSession("qq_token",access_token,request);
                return "/bind";
            }

        }catch (Exception e){
            log.error("qq登录失败！",e);
        }
        return "/login";
    }
    /**
     * qq在线登录绑定/创建用户
     * @param user
     * @param request
     * @return
     */
    @RequestMapping(value = "/qq_bind.htm" ,method = RequestMethod.POST)
    @ResponseBody
    public OpResult qq_login(User user, HttpServletRequest request, HttpServletResponse response){
        OpResult opResult=new OpResult(OpResult.OP_SUCCESS,OpResult.OpMsg.OP_SUCCESS) ;
        try {
            if(user.getUserNo()==null || "".equals(user.getUserNo())){
                log.info("创建账号");
                String url="https://graph.qq.com/user/get_user_info?access_token="+SessionManage.getSession("qq_token",request)+"&oauth_consumer_key="+sysConfigService.getSysConfigByKey(SysConstants.Oauth.QQ_LOGIN_APPID)+"&openid="+SessionManage.getSession("qq_openid",request) ;
                //自动创建
                JSONObject jsonObject=JSONObject.fromObject(HttpClient.getObject(url));
                //注册
                user.setUserPwd(XiaoLinUtil.MD5util.toMD5(user.getUserPwd(),null));    //加密
                user.setQqOpenid(SessionManage.getSession("qq_openid",request).toString());
                user.setHeadImg(jsonObject.getString("figureurl_qq_1"));
                user.setNickName(jsonObject.getString("nickname"));
                user.setBgImg("/static/chat/img/1.png");
                user.setSex("男".equals(jsonObject.getString("gender"))?1:0);
                user.setUserPwd("e10adc3949ba59abbe56e057f20f883e");
                user.setCreateDate(new Date());
                if(userService.insert(user)>0){
                    SessionManage.setSession(SysConstants.USER_KEY,userService.selectUserLogin(user),request);
                    //清理openid
                    SessionManage.deleteSession("qq_openid",request);
                    return   opResult;
                } else{
                    return    new OpResult(OpResult.OP_FAILED, OpResult.OpMsg.OP_FAIL);
                }
            }else{
                log.info("绑定账号");
               //绑定
               User login_user=userService.selectUserLogin(user) ;
                if(login_user!=null){
                    if(login_user.getUserPwd().equals(XiaoLinUtil.MD5util.toMD5(user.getUserPwd(),null))){
                        login_user.setQqOpenid(SessionManage.getSession("qq_openid",request).toString());
                        userService.updateByPrimaryKeySelective(login_user);
                        SessionManage.setSession(SysConstants.USER_KEY,login_user,request);
                        //清理openid
                        SessionManage.deleteSession("qq_openid",request);
                    } else{
                       return  new OpResult(OpResult.OP_FAILED,"密码错误") ;
                    }
                }else{
                    return  new OpResult(OpResult.OP_FAILED,"账号不存在") ;
                }
            }


        }catch (Exception e){
            log.error("qq绑定！",e);
            return  new OpResult(OpResult.OP_FAILED,"绑定出错") ;
        }
        return opResult ;
    }
    /**
     * 获取好友信息
     * @param userId
     * @param request
     * @return
     */
    @RequestMapping(value = "/getFriend.do")
    @ResponseBody
    public String  getFriend(String userId, HttpServletRequest request){
        try {

        } catch (Exception e){
            log.error(e);
        }
        return null;
    }
    /**
     * 验证会员号是否存在
     * @param user
     * @param
     * @return
     */
    @RequestMapping(value = "/yzUser.htm",method = RequestMethod.POST)
    @ResponseBody
    public OpResult yzUser(User user){
        OpResult opResult=new OpResult(OpResult.OP_FAILED, OpResult.OpMsg.OP_FAIL);
        //验证之前是否存在相同
        if(userService.selectUserLogin(user)==null){
            return    new OpResult(OpResult.OP_SUCCESS, OpResult.OpMsg.OP_SUCCESS);
        }
        return opResult;
    }
    @RequestMapping(value = "/exit.htm" )
    public String exit(HttpServletRequest request, HttpServletResponse response) throws IOException {

         User login_user=(User) SessionManage.getSession(SysConstants.USER_KEY,request);
        //清理在线状态
        ChatController chatController=new ChatController();
        chatController.onClose(login_user.getId()+"") ;

        //删除登陆信息
        SessionManage.deleteSession(SysConstants.USER_KEY, request);
               return "/login";
    }

    @RequestMapping(value = "/index.do" )
    public String center_index(ModelMap map, HttpServletRequest request){
        try {
            User login_user=(User)SessionManage.getSession(SysConstants.USER_KEY,request);

            //更新最新资料
            SessionManage.setSession(SysConstants.USER_KEY, userService.getUserDetail(login_user.getId()), request);
            //朋友列表
            List<FriendVo> list_friendVo=new ArrayList<>();
            UserFriend userFriend=new UserFriend();
            userFriend.setUserId(login_user.getId());
            userFriend.setBothFriend(1);
            //获取分组/好友
            UserGroup r_ug=new UserGroup();
            r_ug.setUserId(login_user.getId());
//            List<UserGroup> list_userGroup=userService.get_UserGroupList(r_ug);
            List<UserGroup> list_userGroup=userService.getUserGroupList(r_ug);
            for(UserGroup userGroup:list_userGroup){
               userFriend.setUserGroupId(userGroup.getId());
                //获取分组好友
                List<UserVo> list_friend= userService.get_userFriendList(userFriend);
                FriendVo friendVo=new FriendVo();
                friendVo.setGroupname(userGroup.getName());
                friendVo.setId(userGroup.getId());
                friendVo.setList(list_friend);
                list_friendVo.add(friendVo);
            }

            map.put("friend",JSONArray.toJSONString(list_friendVo));
            //获取我的群组
            GroupUsers groupUsers =new GroupUsers();
            groupUsers.setUserId(login_user.getId());
           List<GroupVo> list_group=userService.getMyGroup(groupUsers);
            map.put("group",JSONArray.toJSONString(list_group));
        } catch (Exception e){
            log.error("获取好友列表出错！",e);
        }
        return "/index";

    }


    @RequestMapping(value = "/serchFriend.do",method = RequestMethod.POST)
    @ResponseBody
    public OpResult serchFriend(User user){
        OpResult opResult=new OpResult(OpResult.OP_SUCCESS, OpResult.OpMsg.OP_SUCCESS);
       //查找好友
        opResult.setDataValue(userService.serchUser(user));
        return opResult;
    }
    /**
     * 好友详情
     */
    @RequestMapping(value = "/userDetail.do")

    public String userDetail(Long id , HttpServletRequest request, ModelMap map){
        try {
            if(id==null){
                return "/friend_info";
            }
            //详情
             map.put("item",userService.getUserDetail(id))   ;
            //是否是我好友
            User login_user=(User)SessionManage.getSession(SysConstants.USER_KEY,request) ;
            UserFriend userFriend=new UserFriend();
            userFriend.setFriendId(id);
            userFriend.setUserId(login_user.getId());
            if(userService.getUserFriendByOne(userFriend)==null){
                //不是好友
                map.put("isfriend",0);
            }else{
                map.put("isfriend",1);
            }
        }catch ( Exception e){
            log.error("错误",e);
        }
        return "/friend_info";
    }

//    /**
//     * 添加好友
//     */
//    @RequestMapping(value = "/addUser.do",method = RequestMethod.POST)
//    @ResponseBody
//    public OpResult addUser(UserFriend userFriend,HttpServletRequest request){
//        OpResult opResult=new OpResult(OpResult.OP_FAILED,"添加失败,或者已经是好友");
//        //添加
//        try {
//            User login_user=(User)SessionManage.getSession(SysConstants.USER_KEY,request);
//            if(userFriend.getFriendId()==null){
//                return opResult;
//            }
//            userFriend.setUserId(login_user.getId());
//            userFriend.setCreateDate(new Date());
//            userFriend.setUpdateDate(new Date());
//            userFriend.setUserGroupId(new Long(1));
//            //添加
//           int a= userService.add_friend(userFriend) ;
//            if(a>0){
//                opResult.setStatus(OpResult.OP_SUCCESS);
//                opResult.setMessage(OpResult.OpMsg.OP_SUCCESS);
//                //推送添加成功，添加当前好友到列表
//                ChatController chatController=new ChatController();
//                //好友资料
//               // User fr =userService.getUserDetail(userFriend.getFriendId()) ;
//                List<UserVo> friendVo = userService.get_userFriendList(userFriend);
//                ChatMsg chatMsg = new ChatMsg();
//                chatMsg.setMsgtype("add");
//                chatMsg.setFromid("0000");
//                chatMsg.setId(friendVo.get(0).getId() + "");
//                chatMsg.setAvatar(friendVo.get(0).getAvatar());
//                chatMsg.setUsername(friendVo.get(0).getUsername());
//                chatMsg.setGroupid(userFriend.getUserGroupId() + "");
//                chatMsg.setSign(friendVo.get(0).getSign());
//
//
//                chatMsg.setType("friend");
//                chatMsg.setTimestamp(new Timestamp(System.currentTimeMillis()).getTime());
////                chatController.sendMessage(JSONObject.fromObject(chatMsg).toString(),login_user.getId().toString(),"0000",3);
//                chatController.sendMessage(JSONObject.fromObject(chatMsg).toString(),userFriend.getFriendId().toString(),"0000",3);
//            }
//        } catch (Exception e){
//            log.error("添加失败",e);
//        }
//        return opResult;
//    }
     /**
     * 获取最近群消息
     */
    @RequestMapping(value = "/get_group_msg.do")
    @ResponseBody
    public OpResult get_group_msg(String userId){
             try {
                 ChatController chatController=new ChatController();
                 //查询群列表，推送最近群消息
                 GroupUsers groupUsers=new GroupUsers();
                 groupUsers.setUserId(new Long(userId));
                 groupUsers.setUsable(SysConstants.isOrNot.NO);
                 List<GroupVo> list_g=userService.getMyGroup(groupUsers);
                 //推送
                 for (GroupVo groupVo:list_g){
                     //查询群消息
                     GroupMsg groupMsg=new GroupMsg();
                     groupMsg.setGroupId(groupVo.getId());
                     groupMsg.setId(groupVo.getMsgId());
                     //获取最近20条记录
                     List<GroupMsg> list_msg= userService.get_group_msgList(groupMsg);
                     //倒叙推送
                     for(int i=list_msg.size()-1 ;i>=0;i--) {
                         Thread.sleep(50);
                         chatController.sendMessage(list_msg.get(i).getMsg(), userId, null, 2);
                     }
                    //修改当前状态为在线
                     groupUsers=new GroupUsers();
                     groupUsers.setId(groupVo.getGroupUserId());
                     groupUsers.setUsable(SysConstants.isOrNot.YES);
                     userService.update_groupUser(groupUsers);
                 }
             } catch (Exception e){
                 log.error("拉取群消息失败");

             }
        return new OpResult(OpResult.OP_SUCCESS,OpResult.OpMsg.OP_SUCCESS) ;
    }
    /**
     * 用户充值
     */
    @RequestMapping(value = "/recharge.do" )
    public String User_recharge(HttpServletRequest request, ModelMap map, String total_fee, String subject, HttpServletResponse response){
        OpResult opResult=new OpResult(OpResult.OP_FAILED,OpResult.OpMsg.OP_FAIL);
        try {
            total_fee="20" ;
            subject="rechange" ;
            //无需验证参数 系统自动提示参数错误
            //获取当钱用户的信息
            User user=(User) SessionManage.getSession(SysConstants.USER_KEY, request);
            if(user==null){
                return "/alipay/alipay_error" ;
            }
            String orderNo = XiaoLinUtil.Util.getdateTime("yyyyMMddHHmmss")+ XiaoLinUtil.Util.getRandom(6);
            //创建本地流水订单
            RechargeOrder order=new RechargeOrder();
            //暂时用来做签名
            order.setCreateDate(new Date());
            order.setOutTradeNo(orderNo);
            order.setSubject(subject);
            order.setTotalFee(total_fee);
            order.setUserid(user.getId());
            order.setDf(0);
            order.setStatus(0);
            if (apiAlipayOrderService.create_recharge_order(order)>0){
                String domain=request.getScheme()+"://"+ request.getServerName();
                AlipayConfig.alipay_app_id= sysConfigService.getSysConfigByKey(SysConstants.ALIPAY_APP_ID);
                AlipayConfig.private_key= sysConfigService.getSysConfigByKey(SysConstants.PRIVATE_KEY);
                AlipayConfig.alipay_public_key= sysConfigService.getSysConfigByKey(SysConstants.ALIPAY_PUBLIC_KEY);

                //1.0


                AlipayClient alipayClient = new DefaultAlipayClient(AlipayConfig.gatewayUrl, AlipayConfig.alipay_app_id, AlipayConfig.private_key, "json", AlipayConfig.charset, AlipayConfig.alipay_public_key, AlipayConfig.sign_type);

                //设置请求参数
                AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
                alipayRequest.setReturnUrl(domain+AlipayConfig.return_url);
                alipayRequest.setNotifyUrl(domain+AlipayConfig.notify_url);
                alipayRequest.setBizContent("{\"out_trade_no\":\""+ order.getOutTradeNo() +"\","
                        + "\"total_amount\":\""+ order.getTotalFee() +"\","
                        + "\"subject\":\""+ order.getSubject() +"\","
                        + "\"body\":\"\","
                        + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");
                //请求
                String htmlUrl = alipayClient.pageExecute(alipayRequest).getBody();
                log.info(sysConfigService.getSysConfigByKey("api_notify_url")+"本次api支付异步地址："+  AlipayConfig.notify_url);
                log.info("本次api支付地址："+ htmlUrl);


                opResult.setStatus(1); //在线支付
                opResult.setMessage(htmlUrl);
                map.put("message",opResult)   ;
                return "/alipay/alipay_manager";
                //  return null;
                // return "redirect:"+htmlUrl;
            } else{
                log.info("创建本地订单失败");
            }
        }catch (Exception e){
            log.error("生成支付请求错误！",e);
        }
        return "/alipay/alipay_error";
    }

    /**
     * 支付成功回掉地址
     */
    @RequestMapping(value = "/return_url.htm")
    public String return_url(HttpServletResponse response, HttpServletRequest request, ModelMap map, String out_trade_no, String trade_no, String trade_status){
        try {
            RechargeOrder order=new RechargeOrder();
            order.setOutTradeNo(out_trade_no);

            order= apiAlipayOrderService.getRechargeOrderDetail(order);
            //获取支付宝GET过来反馈信息
            Map<String,String> params = new HashMap<String,String>();
            Map requestParams = request.getParameterMap();
            for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
                String name = (String) iter.next();
                String[] values = (String[]) requestParams.get(name);
                String valueStr = "";
                for (int i = 0; i < values.length; i++) {
                    valueStr = (i == values.length - 1) ? valueStr + values[i]
                            : valueStr + values[i] + ",";
                }
                //乱码解决，这段代码在出现乱码时使用。如果mysign和sign不相等也可以使用这段代码转化
                valueStr = new String(valueStr.getBytes("ISO-8859-1"), "utf-8");
                if("subject".equals(name)){

                    valueStr = order.getSubject();
                    log.info("sub="+valueStr);
                }
                params.put(name, valueStr);
            }

            //获取支付宝的通知返回参数，可参考技术文档中页面跳转同步通知参数列表(以下仅供参考)//
            //商户订单号
            //支付宝交易号

            //交易状态

            //获取支付宝的通知返回参数，可参考技术文档中页面跳转同步通知参数列表(以上仅供参考)//

            //计算得出通知验证结果
            boolean signVerified = AlipaySignature.rsaCheckV1(params, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type); //调用SDK验证签名


            if(signVerified){//验证成功
                //////////////////////////////////////////////////////////////////////////////////////////
                //请在这里加上商户的业务逻辑程序代码

                //——请根据您的业务逻辑来编写程序（以下代码仅作参考）——


                //判断该笔订单是否在商户网站中已经做过处理
                //如果没有做过处理，根据订单号（out_trade_no）在商户网站的订单系统中查到该笔订单的详细，并执行商户的业务程序
                //请务必判断请求时的total_fee、seller_id与通知时获取的total_fee、seller_id为一致的
                //如果有做过处理，不执行商户的业务程序
                System.out.print("支付成功");
                //注意：
                //付款完成后，支付宝系统发送该交易状态通知
                //支付成功 修改状态 并请求回掉用户的 地址

                if(order!=null){
                    //回掉用户的 地址
                    User user=userService.getUserDetail(order.getUserid());
                    if(order.getStatus()==SysConstants.isOrNot.YES){
                        //已经处理
                        return "redirect:/user/index.do";
                    }

                    //更新用户余额缓存

                    log.info("支付处理成功！正在返回个人中心。。。");
                    return "redirect:/user/index.do";


                }else{
                    //添加错误信息
                    log.error("用户充值后 处理失败:order="+JSONObject.fromObject(order).toString());
                    return "redirect:/user/index.do";
                }


                //——请根据您的业务逻辑来编写程序（以上代码仅作参考）——


                //////////////////////////////////////////////////////////////////////////////////////////
            }else{//验证失败
                return "/alipay/alipay_error";
            }
        } catch (Exception e){

        }
        return "/alipay/alipay_error";
    }

    /**
     * 支付成功回掉地址异步
     */
    @RequestMapping(value = "/notify_url.htm")
    public void notify_url(HttpServletResponse response, HttpServletRequest request, ModelMap map, String out_trade_no, String trade_no, String trade_status){
        try {
            RechargeOrder order=new RechargeOrder();
            order.setOutTradeNo(out_trade_no);

            order= apiAlipayOrderService.getRechargeOrderDetail(order);
            //获取支付宝GET过来反馈信息
            Map<String,String> params = new HashMap<String,String>();
            Map requestParams = request.getParameterMap();
            for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
                String name = (String) iter.next();
                String[] values = (String[]) requestParams.get(name);
                String valueStr = "";
                for (int i = 0; i < values.length; i++) {
                    valueStr = (i == values.length - 1) ? valueStr + values[i]
                            : valueStr + values[i] + ",";
                }
                //乱码解决，这段代码在出现乱码时使用。如果mysign和sign不相等也可以使用这段代码转化
                valueStr = new String(valueStr.getBytes("ISO-8859-1"), "utf-8");
                if("subject".equals(name)){

                    valueStr = order.getSubject();
                    log.info("sub="+valueStr);
                }
                params.put(name, valueStr);
            }
            log.info(params);
            //获取支付宝的通知返回参数，可参考技术文档中页面跳转同步通知参数列表(以下仅供参考)//
            //商户订单号
            //支付宝交易号

            //交易状态

            //获取支付宝的通知返回参数，可参考技术文档中页面跳转同步通知参数列表(以上仅供参考)//

            //计算得出通知验证结果
            boolean signVerified = AlipaySignature.rsaCheckV1(params, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type); //调用SDK验证签名

            log.info("结果："+signVerified);
            if(signVerified){//验证成功
                //////////////////////////////////////////////////////////////////////////////////////////
                //请在这里加上商户的业务逻辑程序代码


                //判断该笔订单是否在商户网站中已经做过处理
                //如果没有做过处理，根据订单号（out_trade_no）在商户网站的订单系统中查到该笔订单的详细，并执行商户的业务程序
                //请务必判断请求时的total_fee、seller_id与通知时获取的total_fee、seller_id为一致的
                //如果有做过处理，不执行商户的业务程序
                System.out.print("支付成功");
                //注意：
                //付款完成后，支付宝系统发送该交易状态通知
                //支付成功 修改状态 并请求回掉用户的 地址

                if(order!=null){
                    //回掉用户的 地址
                    User user=userService.getUserDetail(order.getUserid());
                    if(order.getStatus()==SysConstants.isOrNot.YES){
                        //已经处理
                        //    return "/center/index.vo";
                        return;
                    }
                    //处理记录
                    order.setStatus(1);
                    order.setSerialNumber(trade_no);
                    order.setNewDate(new Date());
                    if(apiAlipayManager.rechargeSuccess(user,order)){
                        //更新用户余额缓存

                        log.info("支付处理成功！正在返回个人中心。。。");
                        //   return "redirect:/center/index.do";

                    } else{
                        //添加错误信息
                        log.error("用户充值后 处理失败:order=" + JSONObject.fromObject(order).toString());
                        //    return "redirect:/center/index.do";
                    }
                }else{
                    //添加错误信息
                    log.error("用户充值后 处理失败:order="+JSONObject.fromObject(order).toString());
                    //   return "redirect:/center/index.do";
                }


                //——请根据您的业务逻辑来编写程序（以上代码仅作参考）——


                //////////////////////////////////////////////////////////////////////////////////////////
            }else{//验证失败
                //  return "/alipay/alipay_error";
                log.error("支付验证失败notify_url");
            }
        } catch (Exception e){

        }
        //  return "/alipay/alipay_error";
    }
    /**
     * 基本资料修改
     */
    @RequestMapping(value = "/updateUser.do")
    @ResponseBody
    public OpResult  updateUser (ModelMap map, User req_user, HttpServletRequest request){
        OpResult opResult=new OpResult(OpResult.OP_FAILED,OpResult.OpMsg.OP_FAIL);
        try {
            User login_user=(User) SessionManage.getSession(SysConstants.USER_KEY, request);
               req_user.setId(login_user.getId());

           if(userService.updateByPrimaryKeySelective(req_user)>0){
               return  new OpResult(OpResult.OP_SUCCESS,OpResult.OpMsg.OP_SUCCESS) ;
           }
            //更新
         //  SessionManage.setSession(SysConstants.USER_KEY, userService.getUserDetail(user.getId()), request);
        } catch (Exception e){
            log.error("资料修改失败!",e);
        }

       return opResult;
    }
    /**
     * 登陆密码修改
     */
    @RequestMapping(value = "/updatePwd.do")
    @ResponseBody
    public OpResult updatePwd (ModelMap map, String oldPwd, String newPwd, HttpServletRequest request){
        OpResult opResult=new OpResult(OpResult.OP_FAILED,"原密码输入错误");
        try {
            User old_user=(User) SessionManage.getSession(SysConstants.USER_KEY, request);
            User user=new User();
            user.setId(old_user.getId());
            //修改登陆密码
            if(old_user.getUserPwd().equals(XiaoLinUtil.MD5util.toMD5(oldPwd, "utf-8"))){
                user.setUserPwd(XiaoLinUtil.MD5util.toMD5(newPwd,"utf-8"));
            }else {
                 return   opResult;
            }

            userService.updateByPrimaryKeySelective(user);
            //更新
            SessionManage.setSession(SysConstants.USER_KEY, userService.getUserDetail(user.getId()), request);
            return  new OpResult(OpResult.OP_SUCCESS, OpResult.OpMsg.OP_SUCCESS);
        } catch (Exception e){
            log.error("资料修改失败!",e);
        }

        return opResult;
    }
    /**
     * 支付密码修改
     */
    @RequestMapping(value = "/updatePayPwd.do")
    @ResponseBody
    public OpResult updatePayPwd (ModelMap map, String oldPwd, String newPwd, HttpServletRequest request){
        OpResult opResult=new OpResult(OpResult.OP_FAILED,"原密码输入错误");
        try {
            User old_user=(User) SessionManage.getSession(SysConstants.USER_KEY, request);
            User user=new User();
            user.setId(old_user.getId());


            userService.updateByPrimaryKeySelective(user);
            //更新
            SessionManage.setSession(SysConstants.USER_KEY, userService.getUserDetail(user.getId()), request);
            return  new OpResult(OpResult.OP_SUCCESS,"修改成功!");
        } catch (Exception e){
            log.error("资料修改失败!",e);
        }

        return opResult;
    }


//    region
    @RequestMapping(value = "newFriend.do")
    public String newFriend(@RequestParam Long id, Model model){
        List<User> user = userService.listRequestFriendsByUserId(id);
        if(user == null){
            user = new ArrayList<User>();
        }
        model.addAttribute("userFriends", user) ;
        return "newFriend";
    }

    /**
     * 重写
     * 添加好友
     */
    @RequestMapping(value = "/addUser.do",method = RequestMethod.POST)
    @ResponseBody
    public OpResult addUser(UserFriend userFriend, HttpServletRequest request){
        OpResult opResult=new OpResult(OpResult.OP_FAILED,"已添加，请耐心等待对方回复");
        //添加
        try {
            User login_user=(User)SessionManage.getSession(SysConstants.USER_KEY,request);
            if(userFriend.getFriendId()==null){
                return opResult;
            }
            userFriend.setUserId(login_user.getId());
            userFriend.setCreateDate(new Date());
            userFriend.setUpdateDate(new Date());
            userFriend.setUserGroupId(new Long(1));
            //添加
            int a= userService.add_friend(userFriend) ;
            if(a == UserServiceImpl.ADD_FRIEND_SUCCESS || a == UserServiceImpl.ADD_FRIEND_MUTUAL){
                opResult.setStatus(OpResult.OP_SUCCESS);
                opResult.setMessage("添加成功");
                ChatController chatController = new ChatController();
//                List<UserVo> friendVo = userService.get_userFriendList(userFriend);
                User userfriend = userService.getUserDetail(userFriend.getFriendId());
                ChatMsg chatMsg = new ChatMsg();
                chatMsg.setMsgtype("add");
                chatMsg.setFromid(login_user.getId().toString());
                chatMsg.setId(login_user.getId().toString());
//                chatMsg.setAvatar(friendVo.get(0).getAvatar());
//                chatMsg.setUsername(friendVo.get(0).getUsername());
//                chatMsg.setSign(friendVo.get(0).getSign());
                chatMsg.setAvatar(userfriend.getHeadImg());
                chatMsg.setUsername(userfriend.getNickName());
                chatMsg.setSign(userfriend.getUserSign());
                chatMsg.setGroupid(userFriend.getUserGroupId() + "");
                chatMsg.setType("friend");
                chatMsg.setTimestamp(new Timestamp(System.currentTimeMillis()).getTime());
                chatController.sendMessage(JSONObject.fromObject(chatMsg).toString(),userFriend.getFriendId().toString(),login_user.getId().toString(),3);
                return opResult;
            }
        } catch (Exception e){
            log.error("添加失败",e);
        }
        return opResult;
    }

//    @RequestMapping(value = "/listHistory.do", method = RequestMethod.POST)
//    @ResponseBody
//    public OpResult listHistory(){
//        OpResult opResult=new OpResult(OpResult.OP_FAILED,"历史记录获取失败");
//        UserMsg userMsg = new UserMsg();
//
//    }

}
