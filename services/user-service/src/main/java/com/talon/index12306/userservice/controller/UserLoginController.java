package com.talon.index12306.userservice.controller;

import com.talon.index12306.convention.result.Result;
import com.talon.index12306.userservice.dto.req.UserLoginReqDTO;
import com.talon.index12306.userservice.dto.req.UserRegisterReqDTO;
import com.talon.index12306.userservice.dto.resp.UserLoginRespDTO;
import com.talon.index12306.userservice.dto.resp.UserRegisterRespDTO;
import com.talon.index12306.userservice.service.UserLoginService;
import com.talon.index12306.web.Results;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

/**
* @author talon
* @description 用户登录控制器
* @Date 2025-09-09
*/

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user-service")
@Tag(name = "用户登录", description = "UserController")
public class UserLoginController {

    private final UserLoginService loginService;

    /**
     *用户登录
     */
    @PostMapping("/v1/login")
    @Operation(summary = "登录")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO requestParam) {
        return Results.success(loginService.login(requestParam));
    }

    /**
     *通过 Token 检查用户是否登录
     */
    @GetMapping("/check-login")
    @Operation(summary = "通过Token检验用户是否登录")
    public Result<UserLoginRespDTO> checkLogin(@RequestParam("accessToken") String accessToken) {
        return Results.success(loginService.checkLogin(accessToken));
    }

    /**
     *用户退出登录
     */
    @GetMapping("/logout")
    @Operation(summary = "登出")
    public Result<Void> logout(@RequestParam(required = false) String accessToken) {
        loginService.logout(accessToken);
        return Results.success();
    }

}
