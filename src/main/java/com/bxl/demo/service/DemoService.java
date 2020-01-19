package com.bxl.demo.service;

import com.bxl.mvcframework.annotation.BxlService;

@BxlService
public interface DemoService {

    String findPassword(String name);
}
