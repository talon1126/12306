package com.talon.index12306.userservice.service.impl;

import cn.hutool.core.util.PhoneUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.convention.exception.ClientException;
import com.talon.index12306.convention.exception.ServiceException;
import com.talon.index12306.framework.user.core.UserContext;
import com.talon.index12306.userservice.dto.req.PassengerRemoveReqDTO;
import com.talon.index12306.userservice.dto.req.PassengerReqDTO;
import com.talon.index12306.userservice.dto.resp.PassengerActualRespDTO;
import com.talon.index12306.userservice.dto.resp.PassengerRespDTO;
import com.talon.index12306.userservice.dao.entity.Passenger;
import com.talon.index12306.userservice.mapstruct.PassengerConverter;
import com.talon.index12306.userservice.service.PassengerService;
import com.talon.index12306.userservice.dao.mapper.PassengerMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.talon.index12306.userservice.common.constant.RedisKeyConstant.USER_PASSENGER_LIST;
import static com.talon.index12306.userservice.common.enums.VerifyStatusEnum.REVIEWED;

/**
 * @author talon
 * @description 乘车人表Service
 * @Date 2025-09-13
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PassengerServiceImpl extends ServiceImpl<PassengerMapper, Passenger> implements PassengerService {

    private final PassengerMapper passengerMapper;
    private final DistributedCache distributedCache;
    private final PassengerConverter passengerConverter;


    @Override
    public List<PassengerRespDTO> listPassengerQueryByUsername(String username) {
        return Optional.ofNullable(getActualUserPassengerListStr(username))
                //数据库明文敏感处理
                .map(str -> JSON.parseArray(str, Passenger.class))
                .map(passengerConverter::Passengers2RespDTOs)
                .orElse(null);
    }

    private String getActualUserPassengerListStr(String username) {
        //先获取缓存
        //明文转化为JSON 解析时脱敏
        return distributedCache.safeGet(
                USER_PASSENGER_LIST + username,
                String.class,
                () ->
                        Optional.ofNullable(list(Wrappers.lambdaQuery(Passenger.class)
                                .eq(Passenger::getUsername, username)
                        )).map(JSON::toJSONString).orElse(null)
                ,
                1,
                TimeUnit.DAYS
        );
    }


    @Override
    public List<PassengerActualRespDTO> listPassengerQueryByIds(String username, List<Long> ids) {
        String s = getActualUserPassengerListStr(username);
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return JSON.parseArray(s, Passenger.class)
                .stream().filter(passenger -> ids.contains(passenger.getId()))
                .map(passengerConverter::Passenger2ActualRespDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void savePassenger(PassengerReqDTO requestParam) {
        //校验参数
        verifyPassenger(requestParam);
        String username = UserContext.getUsername();
        try {
            Passenger passenger = passengerConverter.ReqDTO2Passenger(requestParam);
            passenger.setVerifyStatus(REVIEWED.getCode());
            passenger.setCreateDate(new Date());
            passenger.setUsername(username);
            //插入
            if (!save(passenger)) {
                throw new ServiceException(String.format("[%s] 新增乘车人失败", username));
            }
        } catch (Exception ex) {
            if (ex instanceof ServiceException) {
                log.error("{}，请求参数：{}", ex.getMessage(), JSON.toJSONString(requestParam));
            } else {
                log.error("[{}] 新增乘车人失败，请求参数：{}", username, JSON.toJSONString(requestParam), ex);
            }
            throw ex;
        }
        delUserPassengerCache(username);
    }

    @Override
    public void updatePassenger(PassengerReqDTO requestParam) {
        verifyPassenger(requestParam);
        String username = UserContext.getUsername();
        try {
            Passenger passenger = passengerConverter.ReqDTO2Passenger(requestParam);
            passenger.setUsername(username);
            if (!update(passenger,Wrappers.lambdaUpdate(Passenger.class)
                    .eq(Passenger::getUsername, username)
                    .eq(Passenger::getId, requestParam.getId())
            )) {
                throw new ServiceException(String.format("[%s] 修改乘车人失败", username));
            }
        } catch (Exception ex) {
            if (ex instanceof ServiceException) {
                log.error("{}，请求参数：{}", ex.getMessage(), JSON.toJSONString(requestParam));
            } else {
                log.error("[{}] 修改乘车人失败，请求参数：{}", username, JSON.toJSONString(requestParam), ex);
            }
            throw ex;
        }
        delUserPassengerCache(username);
    }

    @Override
    public void removePassenger(PassengerRemoveReqDTO requestParam) {
        String username = UserContext.getUsername();
        Passenger passenger = selectPassenger(username, requestParam.getId());
        if (passenger == null) {
            throw new ClientException("乘车人数据不存在");
        }
        try {
            if (!remove(Wrappers.lambdaQuery(Passenger.class)
                    .eq(Passenger::getUsername, username)
                    .eq(Passenger::getId, requestParam.getId()))) {
                throw new ServiceException(String.format("[%s] 删除乘车人失败", username));
            }
        } catch (Exception ex) {
            if (ex instanceof ServiceException) {
                log.error("{}，请求参数：{}", ex.getMessage(), JSON.toJSONString(requestParam));
            } else {
                log.error("[{}] 删除乘车人失败，请求参数：{}", username, JSON.toJSONString(requestParam), ex);
            }
            throw ex;
        }
        delUserPassengerCache(username);
    }

    private void verifyPassenger(PassengerReqDTO requestParam) {
        int length = requestParam.getRealName().length();
        if (!(length >= 2 && length <= 16)) {
            throw new ClientException("乘车人名称请设置2-16位的长度");
        }
//        if (!IdcardUtil.isValidCard(requestParam.getIdCard())) {
//            throw new ClientException("乘车人证件号错误");
//        }
        if (!PhoneUtil.isMobile(requestParam.getPhone())) {
            throw new ClientException("乘车人手机号错误");
        }
    }

    private void delUserPassengerCache(String username) {
        distributedCache.delete(USER_PASSENGER_LIST + username);
    }

    private Passenger selectPassenger(String username, String passengerId) {
        return getOne(Wrappers.lambdaQuery(Passenger.class)
                .eq(Passenger::getUsername, username)
                .eq(Passenger::getId, passengerId));
    }

}




