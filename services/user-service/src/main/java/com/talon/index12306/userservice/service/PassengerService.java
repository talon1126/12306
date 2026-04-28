package com.talon.index12306.userservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.talon.index12306.userservice.dto.req.PassengerRemoveReqDTO;
import com.talon.index12306.userservice.dto.req.PassengerReqDTO;
import com.talon.index12306.userservice.dto.resp.PassengerActualRespDTO;
import com.talon.index12306.userservice.dto.resp.PassengerRespDTO;
import com.talon.index12306.userservice.dao.entity.Passenger;

import java.util.List;

/**
* @author talon
* @description 乘车人表Service
* @Date 2025-09-13
*/
public interface PassengerService extends IService<Passenger> {

    /**
     * 根据用户名查询乘车人列表
     *
     * @param username 用户名
     * @return 乘车人返回列表
     */
    List<PassengerRespDTO> listPassengerQueryByUsername(String username);

    /**
     * 根据乘车人 ID 集合查询乘车人列表
     *
     * @param username 用户名
     * @param ids      乘车人 ID 集合
     * @return 乘车人返回列表
     */
    List<PassengerActualRespDTO> listPassengerQueryByIds(String username, List<Long> ids);

    /**
     * 新增乘车人
     *
     * @param requestParam 乘车人信息
     */
    void savePassenger(PassengerReqDTO requestParam);

    /**
     * 修改乘车人
     *
     * @param requestParam 乘车人信息
     */
    void updatePassenger(PassengerReqDTO requestParam);

    /**
     * 移除乘车人
     *
     * @param requestParam 移除乘车人信息
     */
    void removePassenger(PassengerRemoveReqDTO requestParam);
}
