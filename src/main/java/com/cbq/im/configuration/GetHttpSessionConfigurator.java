package com.cbq.im.configuration;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;

/**
 * @Author chen_bq
 * @Description 获取HttpSession
 * @Date 2019/3/4 9:40
 **/
public class GetHttpSessionConfigurator extends Configurator {

    @Override
    public void modifyHandshake(ServerEndpointConfig sec,
                                HandshakeRequest request, HandshakeResponse response) {
        // TODO Auto-generated method stub
        HttpSession httpSession=(HttpSession) request.getHttpSession();
        if(sec.getUserProperties().get(HttpSession.class.getName()) == null){
            sec.getUserProperties().put(HttpSession.class.getName(),httpSession);
        }
        HttpSession httpSession1 = (HttpSession)sec.getUserProperties().get(HttpSession.class.getName());
        System.out.println("当前WebSocket中的sessionId： "+ httpSession1.getId());
    }

}