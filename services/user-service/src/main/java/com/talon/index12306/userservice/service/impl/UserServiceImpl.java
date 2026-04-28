package com.talon.index12306.userservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.talon.index12306.convention.exception.ClientException;
import com.talon.index12306.userservice.dto.req.UserUpdateReqDTO;
import com.talon.index12306.userservice.dto.resp.UserQueryActualRespDTO;
import com.talon.index12306.userservice.dto.resp.UserQueryRespDTO;
import com.talon.index12306.userservice.dao.entity.User;
import com.talon.index12306.userservice.dao.entity.UserDeletion;
import com.talon.index12306.userservice.dao.entity.UserMail;
import com.talon.index12306.userservice.dao.mapper.UserDeletionMapper;
import com.talon.index12306.userservice.dao.mapper.UserMailMapper;
import com.talon.index12306.userservice.dao.mapper.UserMapper;
import com.talon.index12306.userservice.dao.mapper.UserPhoneMapper;
import com.talon.index12306.userservice.mapstruct.UserConverter;
import com.talon.index12306.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserConverter userConverter;
    private final UserDeletionMapper deletionMapper;
    private final UserPhoneMapper phoneMapper;
    private final UserMailMapper mailMapper;


    @Override
    public UserQueryRespDTO queryUserByUserId(String userId) {
        User user = getOne(Wrappers.lambdaQuery(User.class)
                .eq(User::getId, userId)
        );
        if (user == null) {
            throw new ClientException("用户不存在，请检查用户ID是否正确");
        }
        return userConverter.User2UserQueryRespDTO(user);
    }

    @Override
    public UserQueryRespDTO queryUserByUsername(String username) {
        User user = getOne(Wrappers.lambdaQuery(User.class)
                .eq(User::getUsername, username)
        );
        if (user == null) {
            throw new ClientException("用户不存在，请检查用户名是否正确");
        }
        return userConverter.User2UserQueryRespDTO(user);
    }

    @Override
    public UserQueryActualRespDTO queryActualUserByUsername(String username) {
        return userConverter.Resp2Actual(queryUserByUsername(username));
    }

    @Override
    public Integer queryUserDeletionNum(Integer idType, String idCard) {
        return Optional.ofNullable(deletionMapper.selectCount(Wrappers.lambdaQuery(UserDeletion.class)
                .eq(UserDeletion::getIdType, idType)
                .eq(UserDeletion::getIdCard, idCard)
        )).map(Long::intValue).orElse(0);
    }

    @Transactional
    @Override
    public void update(UserUpdateReqDTO requestParam) {
        //检查该用户是否存在
        UserQueryRespDTO userQueryRespDTO = queryUserByUsername(requestParam.getUsername());
        //更新用户
        User user = userConverter.UserUpdateReqDTO2User(requestParam);
        updateById(user);
        //邮箱是否变更
        if (StringUtils.hasText(requestParam.getMail()) &&!userQueryRespDTO.getMail().equals(requestParam.getMail())) {
            //删除邮箱
            mailMapper.delete(Wrappers.lambdaQuery(UserMail.class)
                    .eq(UserMail::getUsername, requestParam.getUsername())
            );
            //插入邮箱
            mailMapper.insert(UserMail.builder()
                    .mail(requestParam.getMail())
                    .username(requestParam.getUsername())
                    .build());
        }

    }
}
