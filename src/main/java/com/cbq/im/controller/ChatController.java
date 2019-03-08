package com.cbq.im.controller;

import com.lingdu.model.GroupMsg;
import com.lingdu.model.GroupUsers;
import com.lingdu.model.UserMsg;
import com.lingdu.service.UserService;
import com.lingdu.util.SysConstants;
import com.lingdu.vo.ChatMsg;
import com.lingdu.vo.GroupVo;
import net.sf.json.JSONObject;
import org.apache.log4j.Logger;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.ContextLoader;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Created with IntelliJ IDEA.
 * User: lingdu
 * Date: 18-9-5
 * Time: 下午12:22
 * //                            _ooOoo_
 * //                           o8888888o
 * //                           88" . "88
 * //                           (| -_- |)
 * //                            O\ = /O
 * //                        ____/`---'\____
 * //                      .   ' \\| |// `.
 * //                       / \\||| : |||// \
 * //                     / _||||| -:- |||||- \
 * //                       | | \\\ - /// | |
 * //                     | \_| ''\---/'' | |
 * //                      \ .-\__ `-` ___/-. /
 * //                   ___`. .' /--.--\ `. . __
 * //                ."" '< `.___\_<|>_/___.' >'"".
 * //               | | : `- \`.;`\ _ /`;.`/ - ` : | |
 * //                 \ \ `-. \_ __\ /__ _/ .-` / /
 * //         ======`-.____`-.___\_____/___.-`____.-'======
 * //                            `=---='
 * //
 * //         .............................................
 * //                  佛祖保佑                  永无BUG
 * //          佛曰:
 * //                  写字楼里写字间，写字间里程序员；
 * //                  程序人员写程序，又拿程序换酒钱。
 * //                  酒醒只在网上坐，酒醉还来网下眠；
 * //                  酒醉酒醒日复日，网上网下年复年。
 * //                  但愿老死电脑间，不愿鞠躬老板前；
 * //                  奔驰宝马贵者趣，公交自行程序员。
 * //                  别人笑我忒疯癫，我笑自己命太贱；
 * //                  不见满街漂亮妹，哪个归得程序员？
 */

/**
 * @ServerEndpoint 注解是一个类层次的注解，它的功能主要是将目前的类定义成一个websocket服务器端,
 * 注解的值将被用于监听用户连接的终端访问URL地址,客户端可以通过这个URL来连接到WebSocket服务器端
 */
@ServerEndpoint(value = "/chat/{userid}"/*, configurator = GetHttpSessionConfigurator.class*/)
public class ChatController {
    //静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
    private static int onlineCount = 0;
    UserService userService = ContextLoader.getCurrentWebApplicationContext().getBean(UserService.class);

    //concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。若要实现服务端与单一客户端通信的话，可以使用Map来存放，其中Key可以为用户标识
    // private static CopyOnWriteArraySet<WebSocketTest> webSocketSet = new CopyOnWriteArraySet<WebSocketTest>();
//线程安全的Map
    private static ConcurrentHashMap<String, Session> listUser = new ConcurrentHashMap();

    private static ConcurrentHashMap<String, Object> userAddr = new ConcurrentHashMap<>();

    private Logger log = Logger.getLogger(getClass());

    /**
     * 连接建立成功调用的方法
     *
     * @param session 可选的参数。session为与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userid") String userid) {

        listUser.put(userid, session);     //加入set中

        System.out.println("有新连接加入！当前在线人数为" + listUser.size());

        //查询离线推送消息
        UserMsg userMsg = new UserMsg();
        userMsg.setToUserId(new Long(userid));
        List<UserMsg> list = userService.get_myUserMsg(userMsg);

        //推送
        for (UserMsg msg : list) {
            sendMessage(msg.getMsg(), userid, null, 1);
            //修改已读
            UserMsg update_msg = new UserMsg();
            if(msg.getUsable() == SysConstants.isOrNot.NO){
                update_msg.setId(msg.getId());
                update_msg.setUsable(SysConstants.isOrNot.NO);
                userService.update_userMsg(update_msg);
            }
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(@PathParam("userid") String userid) {
        listUser.remove(userid);  //从set中删除
        System.out.println("有一连接关闭！当前在线人数为" + listUser.size());

        //退出登录后，报错当前所有群消息 和离线状态
        //查询群列表，推送最近群消息
        GroupUsers groupUsers = new GroupUsers();
        groupUsers.setUserId(new Long(userid));
        List<GroupVo> list_g = userService.getMyGroup(groupUsers);
        //推送
        for (GroupVo groupVo : list_g) {
            //查询群消息
            GroupMsg groupMsg = new GroupMsg();
            groupMsg.setGroupId(groupVo.getId());
            groupMsg.setId(groupVo.getMsgId());
            //获取最近50条记录
            List<GroupMsg> list_msg = userService.get_group_msgList(groupMsg);
            //有新消息
            Long msgId = groupVo.getMsgId();
            if (list_msg.size() > 0 && list_msg != null) {
                msgId = list_msg.get(0).getId();
            }
            //修改当前状态为离线  并且保留最新群消息
            groupUsers = new GroupUsers();
            groupUsers.setId(groupVo.getGroupUserId());
            groupUsers.setUsable(SysConstants.isOrNot.NO);
            groupUsers.setUpdateDate(new Date());
            groupUsers.setMsgId(msgId);
            userService.update_groupUser(groupUsers);
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     * @param
     */
    @OnMessage
    public void onMessage(String message, @PathParam("userid") String userid) {

        JSONObject msg = JSONObject.fromObject(message);
        if ("message".equals(msg.getString("type"))) {
            //消息类型
            JSONObject mine = msg.getJSONObject("data").getJSONObject("mine");
            JSONObject to = msg.getJSONObject("data").getJSONObject("to");
            System.out.println("来自客户端的消息:" + userid + "发送给" + to.getString("id") + ":  " + mine.getString("content"));
            try {
                ChatMsg chatMsg = new ChatMsg();
                chatMsg.setMsgtype("message");
                chatMsg.setFromid(userid);
                chatMsg.setAvatar(mine.getString("avatar"));
                chatMsg.setUsername(mine.getString("username"));
                chatMsg.setContent(mine.getString("content"));

                chatMsg.setType(to.getString("type"));
                chatMsg.setTimestamp(new Timestamp(System.currentTimeMillis()).getTime());

                //发送指定用户
                // 如果是群 发送给群成员
                if ("group".equals(chatMsg.getType())) {
                    //群就是群id
                    chatMsg.setId(to.getString("id"));
                    //查询群成员   缓存。成员变动 必须更新缓存
                    GroupUsers groupUsers = new GroupUsers();
                    groupUsers.setGroupId(new Long(to.getString("id")));
                    List<GroupUsers> list = userService.getGroupUsers(groupUsers);
                    for (GroupUsers g : list) {
                        //不发送给自己
                        if (!g.getUserId().toString().equals(userid)) {
                            sendMessage(JSONObject.fromObject(chatMsg).toString(), g.getUserId().toString(), userid, 2);
                        }
                    }
                    //保存群消息
                    GroupMsg groupMsg = new GroupMsg();
                    groupMsg.setFromUser(new Long(userid));
                    groupMsg.setGroupId(to.getLong("id"));
                    groupMsg.setMsg(JSONObject.fromObject(chatMsg).toString());
                    groupMsg.setCreateDate(new Date());

                    userService.insert_group_msg(groupMsg);

                } else {
                    //发送者id
                    chatMsg.setId(userid);
                    sendMessage(JSONObject.fromObject(chatMsg).toString(), to.getString("id"), userid, 1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }


    }

    /**
     * 发生错误时调用
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        System.out.println("发生错误 : " + error.getMessage());
//        error.printStackTrace();
    }

    /**
     * 这个方法与上面几个方法不一样。没有用注解，是根据自己需要添加的方法。
     *
     * @param message
     * @param type    1好友消息，2群消息，3系统消息
     * @throws IOException
     */
    public void sendMessage(String message, String toUserId, String fromUserId, int type) {
        try {
            if (type == 1) {
                //好友消息
                //保存聊天记录到数据库
                UserMsg userMsg = new UserMsg();
                userMsg.setCreateDate(new Date());
                userMsg.setMsg(JSONObject.fromObject(message).toString());
                userMsg.setToUserId(new Long(toUserId));
                //接收者
                Session session = listUser.get(toUserId);
                if (session != null) {
                    if (session.isOpen()) {
                        session.getAsyncRemote().sendText(message);
                        userMsg.setUsable(SysConstants.isOrNot.NO);
                    } else {
                        //离线消息 下次推送
                        userMsg.setUsable(SysConstants.isOrNot.YES);
                    }
                } else {
                    //离线消息 下次推送
                    userMsg.setUsable(SysConstants.isOrNot.YES);
                }


                //添加到数据库
                if (fromUserId != null) {
                    //关系编号 遵循 小id在前的string 拼接
                    userMsg.setFriendId(Integer.parseInt(fromUserId) > Integer.parseInt(toUserId) ? new Long(toUserId + fromUserId) : new Long(fromUserId + toUserId));
                    userService.insertUserMsg(userMsg);
                }
            } else if (type == 2) {
                //群聊消息
                //接收者
                Session session = listUser.get(toUserId);
                if (session != null) {
                    if (session.isOpen()) {
                        session.getAsyncRemote().sendText(message);
                    }
                }
            } else if (type == 3) {
                //系统消息
                //接收者
                Session session = listUser.get(toUserId);
                if (session != null) {
                    if (session.isOpen()) {
                        session.getAsyncRemote().sendText(message);
                    }
                }
            }


        } catch (Exception e) {
            log.error("发送出错！", e);
        }

    }

    /**
     * @return void
     * @Author chen_bq
     * @Description 手动下线
     * @Date 2019/3/1 15:45
     * @Param [userId]
     **/
    public void offLine(@RequestParam String userId) {
        listUser.remove(userId);
        System.out.println("异地登录，原用户下线");
    }

    /**
     * 根据key获得在线用户的session信息
     *
     * @return
     */
    public Session getOnlineSessionByUserId(@RequestParam String userId) {
        return listUser.get(userId);
    }

    public String getUserAddr(String key) {
        return (String) userAddr.get(key);
    }

    public void addUserAddr(String key, String value) {
        userAddr.put(key,value);
    }
}