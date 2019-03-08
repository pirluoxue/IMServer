//package com.cbq.im.filter;
//
//
//import com.cbq.im.controller.ChatController;
//import com.google.common.base.Strings;
//import org.apache.log4j.Logger;
//import org.springframework.stereotype.Component;
//
//import javax.servlet.*;
//import javax.servlet.annotation.WebFilter;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//import javax.servlet.http.HttpServletResponseWrapper;
//import java.io.IOException;
//
//
//@Component
//@WebFilter(urlPatterns = "/webapi/*", filterName = "authFilter")
//public class MainFilter implements Filter {
//    private final static Logger log = Logger.getLogger(MainFilter.class);
//
//    public FilterConfig config;
//
//    @Override
//    public void destroy() {
//        this.config = null;
//    }
//
//    public static boolean isContains(String container, String[] regx) {
//        boolean result = false;
//
//        for (int i = 0; i < regx.length; i++) {
//            if (container.indexOf(regx[i]) != -1) {
//                return true;
//            }
//        }
//        return result;
//    }
//
//    @Override
//    public void init(FilterConfig filterConfig) throws ServletException {
//        config = filterConfig;
//    }
//
//    /**
//     * 该环境的过滤方式 为*.do  全部过滤登陆信息
//     * 不需要登陆验证的 请用.htm请求数据
//     *
//     * @param request
//     * @param response
//     * @param chain
//     * @throws IOException
//     * @throws javax.servlet.ServletException
//     */
//    @Override
//    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
//        HttpServletRequest hrequest = (HttpServletRequest) request;
//        HttpServletResponseWrapper wrapper = new HttpServletResponseWrapper((HttpServletResponse) response);
//        //.do进行拦截过滤 全部严重登陆信息
//        Object obj = SessionManage.getSession(SysConstants.USER_KEY, hrequest);
//        if (obj == null) {
//            wrapper.sendRedirect("/login.jsp?returnUrl=" + ((HttpServletRequest) request).getRequestURL());
//            return;
//        }
//
//        /*判断是否为异地登录*/
//        User user = (User) obj;
//        String userId = user.getId().toString();
//        String userAddrValue = new ChatController().getUserAddr(userId);
//        if (!Strings.isNullOrEmpty(userAddrValue)) {
//            //判断当前session是否为最新的websocket上的sessionId
//            if (!userAddrValue.equals(SessionManage.getSession(userId, (HttpServletRequest)request))) {
//                //被挤下去的登录用户
//                SessionManage.deleteSession(SysConstants.USER_KEY, (HttpServletRequest)request);
//                SessionManage.deleteSession(userId, (HttpServletRequest)request);
//
//                wrapper.sendRedirect("/login.jsp");
//                return;
//            }
//        }
//        /*判断是否为异地登录*/
//
//        if (((User) obj).getUsable() == null || ((User) obj).getUsable() == SysConstants.isOrNot.NO) {
//            //用户被禁用
//            wrapper.sendRedirect("/alipay/alipay_error.jsp");
//            return;
//        }
//        chain.doFilter(request, response);
//        return;
////
////        String logonStrings = config.getInitParameter("logonStrings");        // 登录登陆页面
////        String includeStrings = config.getInitParameter("includeStrings");    // 过滤资源后缀参数
////        String redirectPath = hrequest.getContextPath() + config.getInitParameter("redirectPath");// 没有登陆转向页面
////        String disabletestfilter = config.getInitParameter("disabletestfilter");// 过滤器是否有效
////
////        if (disabletestfilter.toUpperCase().equals("N")) {    // 过滤无效
////            chain.doFilter(request, response);
////            return;
////        }
////        String[] logonList = logonStrings.split(";");
////        String[] includeList = includeStrings.split(";");
////        log.debug("本次请求地址："+hrequest.getRequestURI());
////        if (!this.isContains(hrequest.getRequestURI(), includeList)) {// 只对指定过滤参数后缀进行过滤
////            chain.doFilter(request, response);
////            return;
////        }
////
////        if (this.isContains(hrequest.getRequestURI(), logonList)) {// 对登录页面不进行过滤
////            chain.doFilter(request, response);
////            return;
////        }
//
//        //       chain.doFilter(request, response);
//        //       return;
////        String user = ( String ) hrequest.getSession().getAttribute("user");//判断用户是否登录
////        if (user == null) {
////            wrapper.sendRedirect(redirectPath);
////            return;
////        }else {
////            chain.doFilter(request, response);
////            return;
////        }
//    }
//
//
//}
