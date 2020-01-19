package com.bxl.demo.api;

import com.bxl.demo.service.DemoService;
import com.bxl.mvcframework.annotation.BxlAutowried;
import com.bxl.mvcframework.annotation.BxlController;
import com.bxl.mvcframework.annotation.BxlRequestMapping;
import com.bxl.mvcframework.annotation.BxlRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@BxlController
@BxlRequestMapping("/demo")
public class DemoApi {

    @BxlAutowried
    private DemoService demoService;

    @BxlRequestMapping("/findPassword")
    public void  findPassword(HttpServletRequest req, HttpServletResponse resp, @BxlRequestParam("name") String name) throws IOException {
        String password = demoService.findPassword(name);
        if (password!=null){
            //todo。。。
            resp.getWriter().print(name+"（先生/女士）您的密码是："+password+"，请注意保管好！");
        } else {
            resp.getWriter().print("抱歉,"+name+"（先生/女士），这里没有查到您的密码！");
        }
    }



}
