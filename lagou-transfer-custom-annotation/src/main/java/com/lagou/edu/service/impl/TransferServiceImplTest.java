package com.lagou.edu.service.impl;


import com.lagou.edu.factory.BeanFactory;
import com.lagou.edu.service.TransferService;

class TransferServiceImplTest {
    public static void main(String[] args) throws Exception {
        TransferService transferService = (TransferService) BeanFactory.getBean("transferService");
        transferService.transfer("001", "002",100);
    }

}