package com.talon.index12306.userservice.controller;

import com.talon.index12306.convention.result.Result;
import com.talon.index12306.userservice.dto.req.UserDeletionReqDTO;
import com.talon.index12306.userservice.dto.req.UserRegisterReqDTO;
import com.talon.index12306.userservice.dto.req.UserUpdateReqDTO;
import com.talon.index12306.userservice.dto.resp.UserQueryActualRespDTO;
import com.talon.index12306.userservice.dto.resp.UserQueryRespDTO;
import com.talon.index12306.userservice.dto.resp.UserRegisterRespDTO;
import com.talon.index12306.userservice.service.UserLoginService;
import com.talon.index12306.userservice.service.UserService;
import com.talon.index12306.web.Results;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user-service")
@Tag(name = "用户信息", description = "UserInfoController")
public class UserInfoController {

    private final UserLoginService loginService;
    private final UserService userService;

    /**
     * 根据用户名查询用户信息
     */
    @GetMapping("/query")
    public Result<UserQueryRespDTO> queryUserByUsername(@RequestParam("username") @NotEmpty String username) {
        return Results.success(userService.queryUserByUsername(username));
    }

    /**
     * 根据用户名查询用户无脱敏信息
     */
    @GetMapping("/actual/query")
    public Result<UserQueryActualRespDTO> queryActualUserByUsername(@RequestParam("username") @NotEmpty String username) {
        return Results.success(userService.queryActualUserByUsername(username));
    }

    /**
     * 检查用户名是否已存在
     */
    @GetMapping("/has-username")
    public Result<Boolean> hasUsername(@RequestParam("username") @NotEmpty String username) {
        return Results.success(loginService.hasUsername(username));
    }

    /**
     * 注册用户
     */
    @PostMapping("/register")
    public Result<UserRegisterRespDTO> register(@RequestBody @Valid UserRegisterReqDTO requestParam) {
        return Results.success(loginService.register(requestParam));
    }

    /**
     * 修改用户
     */
    @PostMapping("/update")
    public Result<Void> update(@RequestBody @Valid UserUpdateReqDTO requestParam) {
        userService.update(requestParam);
        return Results.success();
    }

    /**
     * 注销用户
     */
    @PostMapping("/deletion")
    public Result<Void> deletion(@RequestBody @Valid UserDeletionReqDTO requestParam) {
        loginService.deletion(requestParam);
        return Results.success();
    }
}