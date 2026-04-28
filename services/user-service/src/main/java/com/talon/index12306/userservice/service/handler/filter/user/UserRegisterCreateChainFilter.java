package com.talon.index12306.userservice.service.handler.filter.user;

import com.talon.index12306.designpattern.chain.AbstractChainHandler;
import com.talon.index12306.userservice.common.enums.UserChainMarkEnum;
import com.talon.index12306.userservice.dto.req.UserRegisterReqDTO;

public interface UserRegisterCreateChainFilter<T extends UserRegisterReqDTO> extends AbstractChainHandler<UserRegisterReqDTO> {

    @Override
    default String mark() {return UserChainMarkEnum.USER_REGISTER_FILTER.name();}


}
