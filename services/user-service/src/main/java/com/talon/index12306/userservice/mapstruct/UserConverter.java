package com.talon.index12306.userservice.mapstruct;

import com.talon.index12306.userservice.dto.req.UserRegisterReqDTO;
import com.talon.index12306.userservice.dto.req.UserUpdateReqDTO;
import com.talon.index12306.userservice.dto.resp.UserQueryActualRespDTO;
import com.talon.index12306.userservice.dto.resp.UserQueryRespDTO;
import com.talon.index12306.userservice.dto.resp.UserRegisterRespDTO;
import com.talon.index12306.userservice.dao.entity.User;
import com.talon.index12306.userservice.dao.entity.UserMail;
import com.talon.index12306.userservice.dao.entity.UserPhone;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserConverter {

    User UserRegisterReqDTO2User(UserRegisterReqDTO dto);

    User UserUpdateReqDTO2User(UserUpdateReqDTO dto);

    UserMail  UserRegisterReqDTO2UserMail(UserRegisterReqDTO dto);

    UserPhone UserRegisterReqDTO2UserPhone(UserRegisterReqDTO dto);

    UserRegisterRespDTO Req2Resp(UserRegisterReqDTO dto);

    UserQueryRespDTO User2UserQueryRespDTO(User user);

    UserQueryActualRespDTO Resp2Actual(UserQueryRespDTO dto);

}
