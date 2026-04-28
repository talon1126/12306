package com.talon.index12306.userservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.convention.exception.ClientException;
import com.talon.index12306.convention.exception.ServiceException;
import com.talon.index12306.designpattern.chain.AbstractChainContext;
import com.talon.index12306.framework.user.core.UserContext;
import com.talon.index12306.framework.user.core.UserInfoDTO;
import com.talon.index12306.framework.user.util.JwtUtil;
import com.talon.index12306.userservice.common.enums.UserChainMarkEnum;
import com.talon.index12306.userservice.dao.entity.*;
import com.talon.index12306.userservice.dao.mapper.*;
import com.talon.index12306.userservice.dto.req.UserDeletionReqDTO;
import com.talon.index12306.userservice.dto.req.UserLoginReqDTO;
import com.talon.index12306.userservice.dto.req.UserRegisterReqDTO;
import com.talon.index12306.userservice.dto.resp.UserLoginRespDTO;
import com.talon.index12306.userservice.dto.resp.UserQueryRespDTO;
import com.talon.index12306.userservice.dto.resp.UserRegisterRespDTO;
import com.talon.index12306.userservice.mapstruct.UserConverter;
import com.talon.index12306.userservice.service.UserLoginService;
import com.talon.index12306.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.talon.index12306.userservice.common.constant.RedisKeyConstant.*;
import static com.talon.index12306.userservice.common.enums.UserRegisterErrorCodeEnum.*;
import static com.talon.index12306.userservice.util.UserReuseUtil.hashShardingIdx;

/**
 * @author talon
 * @description 用户表Service
 * @Date 2025-09-09
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserLoginServiceImpl extends ServiceImpl<UserMapper, User> implements UserLoginService {

    private final UserMapper userMapper;
    private final UserMailMapper mailMapper;
    private final UserPhoneMapper phoneMapper;
    private final DistributedCache distributedCache;
    private final RedissonClient redissonClient;
    private final UserConverter userConverter;
    private final UserService userService;
    private final UserDeletionMapper deletionMapper;
    private final AbstractChainContext<UserRegisterReqDTO> abstractChainContext;
    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final UserReuseMapper userReuseMapper;



    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        String s = requestParam.getUsernameOrMailOrPhone();
        if (StringUtils.hasText(s)) {
            String username;
            boolean mail = false;
            for (char c : s.toCharArray()) {
                if (c == '@') {
                    mail = true;
                    break;
                }
            }
            //mail
            if (mail) {
                username = Optional.ofNullable(mailMapper.selectOne(Wrappers.lambdaQuery(UserMail.class)
                        .eq(UserMail::getMail, s)
                )).map(UserMail::getUsername).orElseThrow(() -> new ClientException("用户名/手机号/邮箱不存在"));
            } else {
                //phone
                username = Optional.ofNullable(phoneMapper.selectOne(Wrappers.lambdaQuery(UserPhone.class)
                        .eq(UserPhone::getPhone, s))).map(UserPhone::getUsername).orElse(null);
            }
            //username
            if (username == null) username = requestParam.getUsernameOrMailOrPhone();
            User user = getOne(Wrappers.lambdaQuery(User.class)
                    .eq(User::getUsername, username)
                    .eq(User::getPassword, requestParam.getPassword())
                    .select(User::getId, User::getUsername, User::getRealName)
            );
            //生成token
            if (user != null) {
                UserInfoDTO userInfo = UserInfoDTO.builder()
                        .userId(user.getId().toString())
                        .username(user.getUsername())
                        .realName(user.getRealName())
                        .build();
                String token = JwtUtil.generateToken(userInfo);
                UserLoginRespDTO res = UserLoginRespDTO.builder()
                        .userId(user.getId().toString())
                        .accessToken(token)
                        .username(user.getUsername())
                        .realName(user.getRealName())
                        .build();
                distributedCache.put(token, res, 30, TimeUnit.MINUTES);
                return res;
            }
        }
        throw new ServiceException("账号不存在或密码错误");
    }

    @Override
    public UserLoginRespDTO checkLogin(String accessToken) {
        return distributedCache.get(accessToken, UserLoginRespDTO.class);
    }

    @Override
    public void logout(String accessToken) {
        if (StringUtils.hasText(accessToken)) {
            distributedCache.delete(accessToken);
        }
    }

    @Override
    public Boolean hasUsername(String username) {
        if (userRegisterCachePenetrationBloomFilter.contains(username)){
            //布隆过滤器中存在 但不确定用户名是否被注销
            StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
            return instance.opsForSet().isMember(USER_REGISTER_REUSE_SHARDING + hashShardingIdx(username), username);
        }
       return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public UserRegisterRespDTO register(UserRegisterReqDTO requestParam) {
        abstractChainContext.handler(UserChainMarkEnum.USER_REGISTER_FILTER.name(), requestParam);
        //根据username加锁
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER + requestParam.getUsername());
        if (!lock.tryLock()) {
            throw new ServiceException(HAS_USERNAME_NOTNULL);
        }
        User user = userConverter.UserRegisterReqDTO2User(requestParam);
        try {
            //插入用户表
            try {
                if (userMapper.insert(user) <= 0) {
                    throw new ServiceException(USER_REGISTER_FAIL);
                }
            } catch (DuplicateKeyException e) {
                log.error("用户名 [{}] 重复注册", requestParam.getUsername());
                throw new ServiceException(HAS_USERNAME_NOTNULL);
            }
            //phone
            try {
                phoneMapper.insert(userConverter.UserRegisterReqDTO2UserPhone(requestParam));
            } catch (DuplicateKeyException e) {
                log.error("用户 [{}] 注册手机号 [{}] 重复", requestParam.getUsername(), requestParam.getPhone());
                throw new ServiceException(PHONE_REGISTERED);
            }
            //mail
            if (user.getMail() != null) {
                try {
                    mailMapper.insert(userConverter.UserRegisterReqDTO2UserMail(requestParam));
                } catch (DuplicateKeyException e) {
                    log.error("用户 [{}] 注册邮箱 [{}] 重复", requestParam.getUsername(), requestParam.getMail());
                    throw new ServiceException(MAIL_REGISTERED);
                }
            }
            String username = requestParam.getUsername();
            UserReuse userReuse = new UserReuse();
            userReuse.setUsername(username);
            userReuseMapper.delete(Wrappers.update(userReuse));
            StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
            //set中删除
            instance.opsForSet().remove(USER_REGISTER_REUSE_SHARDING + hashShardingIdx(username), username);
            //用户名添加到bloomFilter
            userRegisterCachePenetrationBloomFilter.add(username);
        } finally {
            lock.unlock();
        }
        return userConverter.Req2Resp(requestParam);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deletion(UserDeletionReqDTO requestParam) {
        //检查与登录用户名是否相同
        String username = requestParam.getUsername();
        if (!UserContext.getUsername().equals(username)) {
            throw new ClientException("注销账号与登录账号不一致");
        }
        //加删除锁
        RLock lock = redissonClient.getLock(USER_DELETION + username);
        lock.lock();
        try {
            UserQueryRespDTO userQueryRespDTO = userService.queryUserByUsername(username);
            //插入删除表
            deletionMapper.insert(UserDeletion.builder()
                    .idType(userQueryRespDTO.getIdType())
                    .idCard(userQueryRespDTO.getIdCard())
                    .build());
            //逻辑删除
            userMapper.delete(Wrappers.lambdaQuery(User.class)
                    .eq(User::getUsername, username)
            );
            phoneMapper.delete(Wrappers.lambdaQuery(UserPhone.class)
                    .eq(UserPhone::getPhone, userQueryRespDTO.getPhone())
            );
            if (userQueryRespDTO.getMail() != null) {
                mailMapper.delete(Wrappers.lambdaQuery(UserMail.class)
                        .eq(UserMail::getMail, userQueryRespDTO.getMail())
                );
            }
            //删除缓存
            distributedCache.delete(UserContext.getToken());
            UserReuse userReuse = new UserReuse();
            userReuse.setUsername(requestParam.getUsername());
            userReuseMapper.insert(userReuse);
            StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
            instance.opsForSet().add(USER_REGISTER_REUSE_SHARDING + hashShardingIdx(username), username);
        } finally {
            lock.unlock();
        }
    }
}




