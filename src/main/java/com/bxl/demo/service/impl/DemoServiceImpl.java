package com.bxl.demo.service.impl;

import com.bxl.demo.service.DemoService;
import com.bxl.mvcframework.annotation.BxlService;

import java.util.HashMap;
import java.util.Map;

@BxlService
public class DemoServiceImpl implements DemoService {

    private static final Map<String,String> passwordMap = new HashMap();

    static {
        passwordMap.put("bxl","12345");
        passwordMap.put("lsj","35326");
        passwordMap.put("zxx","gierteg333");
        passwordMap.put("admin","admin");
        passwordMap.put("root","ssf$i%U43");
    }


    public String findPassword(String name) {
        return passwordMap.get(name);
    }


}
