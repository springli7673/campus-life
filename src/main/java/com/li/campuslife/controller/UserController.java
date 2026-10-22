package com.li.campuslife.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.li.campuslife.dto.LoginFormDTO;
import com.li.campuslife.dto.ProfileDTO;
import com.li.campuslife.dto.Result;
import com.li.campuslife.dto.UserDTO;
import com.li.campuslife.entity.User;
import com.li.campuslife.entity.UserInfo;
import com.li.campuslife.service.IUserInfoService;
import com.li.campuslife.service.IUserService;
import com.li.campuslife.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import static com.li.campuslife.utils.RedisConstants.LOGIN_USER_KEY;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        //实现登录功能
        return userService.login(loginForm, session);
    }

    /**
     * 登出：删掉 Redis 里的 token，前端清掉本地 token 即可
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token) {
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        }
        return Result.ok();
    }

    /**
     * 当前登录用户。直接从库里取，改完资料立刻能刷出来（token 里缓存的是旧昵称）
     */
    @GetMapping("/me")
    public Result me(){
        UserDTO cached = UserHolder.getUser();
        if (cached == null) {
            return Result.ok();
        }
        User user = userService.getById(cached.getId());
        if (user == null) {
            return Result.ok(cached);
        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 保存资料：昵称/头像写 tb_user，简介/城市/性别/生日写 tb_user_info
     */
    @PostMapping("/info")
    public Result updateInfo(@RequestBody ProfileDTO dto) {
        UserDTO current = UserHolder.getUser();
        if (current == null) {
            return Result.fail("请先登录");
        }

        String nickName = StrUtil.trim(dto.getNickName());
        if (StrUtil.isNotBlank(nickName) && nickName.length() > 20) {
            return Result.fail("昵称不能超过 20 个字");
        }
        String introduce = StrUtil.trim(dto.getIntroduce());
        if (introduce != null && introduce.length() > 128) {
            return Result.fail("个人介绍不能超过 128 个字");
        }
        String city = StrUtil.trim(dto.getCity());
        if (city != null && city.length() > 64) {
            return Result.fail("城市名称过长");
        }

        User user = new User().setId(current.getId());
        if (StrUtil.isNotBlank(nickName)) {
            user.setNickName(nickName);
        }
        if (StrUtil.isNotBlank(dto.getIcon())) {
            user.setIcon(dto.getIcon());
        }
        if (user.getNickName() != null || user.getIcon() != null) {
            userService.updateById(user);
        }

        boolean hasInfo = introduce != null || city != null
                || dto.getGender() != null || dto.getBirthday() != null;
        if (hasInfo) {
            userInfoService.saveOrUpdate(new UserInfo()
                    .setUserId(current.getId())
                    .setIntroduce(introduce)
                    .setCity(city)
                    .setGender(dto.getGender())
                    .setBirthday(dto.getBirthday()));
        }
        return Result.ok();
    }

    /**
     * 根据id查询用户
     * @param userId
     * @return
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        //查询详情
        User user = userService.getById(userId);
        if(user==null){
            return Result.ok();
        }
        UserDTO userDTO= BeanUtil.copyProperties(user, UserDTO.class);
        //返回
        return Result.ok(userDTO);
    }

    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }

}
