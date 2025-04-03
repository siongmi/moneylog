package org.codenova.moneylog.controller;


import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codenova.moneylog.entity.User;
import org.codenova.moneylog.entity.Verification;
import org.codenova.moneylog.repository.UserRepository;
import org.codenova.moneylog.repository.VerificationRepository;
import org.codenova.moneylog.request.FindPasswordRequest;
import org.codenova.moneylog.request.LoginRequest;
import org.codenova.moneylog.service.KakaoApiService;
import org.codenova.moneylog.service.MailService;
import org.codenova.moneylog.service.NaverApiService;
import org.codenova.moneylog.vo.KakaoTokenResponse;
import org.codenova.moneylog.vo.NaverProfileResponse;
import org.codenova.moneylog.vo.NaverTokenResponse;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
@Controller
@RequestMapping("/auth")
public class AuthController {

    private NaverApiService naverApiService;
    private KakaoApiService kakaoApiService;
    private MailService mailService;
    private UserRepository userRepository;
    private VerificationRepository verificationRepository;


    @GetMapping("/login")
    public String loginHandle(Model model) {
        // log.info("loginHandle...executed");

        model.addAttribute("kakaoClientId", "e5322e89a3a80aa88d40b62fd144f35a");
        model.addAttribute("kakaoRedirectUri", "http://192.168.10.96:8080/auth/kakao/callback");


        model.addAttribute("naverClientId", "UlyY91gdd4hMzViz__oP");
        model.addAttribute("naverRedirectUri", "http://192.168.10.96:8080/auth/naver/callback");


        return "auth/login";
    }


    @PostMapping("/login")
    public String loginPostHandle(
            @ModelAttribute LoginRequest loginRequest,
            HttpSession session,
            Model model) {
        User user =
                userRepository.findByEmail(loginRequest.getEmail());
        if (user != null && user.getPassword().equals(loginRequest.getPassword())) {
            session.setAttribute("user", user);
            return "redirect:/index";
        } else {
            return "redirect:/auth/login";
        }
    }


    @GetMapping("/signup")
    public String signupGetHandle(Model model) {

        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signupPostHandle(@ModelAttribute User user) {
        User found = userRepository.findByEmail(user.getEmail());
        if (found == null) {
            user.setProvider("LOCAL");
            user.setVerified("F");
            userRepository.save(user);
            mailService.sendWelcomeMessage(user);
        }
        return "redirect:/index";
    }

    @GetMapping("/naver/callback")
    public String naverCallbackHandle(@RequestParam("code") String code,
                                      @RequestParam("state") String state, HttpSession session) throws JsonProcessingException {
        // log.info("code = {}, state = {}", code, state);

        NaverTokenResponse tokenResponse =
                naverApiService.exchangeToken(code, state);

        // log.info("accessToken = {}", tokenResponse.getAccessToken());


        NaverProfileResponse profileResponse
                = naverApiService.exchangeProfile(tokenResponse.getAccessToken());
        log.info("profileResponse id = {}", profileResponse.getId());
        log.info("profileResponse nickname = {}", profileResponse.getNickname());
        log.info("profileResponse profileImage = {}", profileResponse.getProfileImage());

        User found = userRepository.findByProviderAndProviderId("NAVER", profileResponse.getId());
        if (found == null) {
            User user = User.builder()
                    .nickname(profileResponse.getNickname())
                    .provider("NAVER")
                    .providerId(profileResponse.getId())
                    .verified("T")
                    .picture(profileResponse.getProfileImage()).build();

            userRepository.save(user);
            session.setAttribute("user", user);

        } else {
            session.setAttribute("user", found);
        }
        return "redirect:/index";
    }


    @GetMapping("/kakao/callback")
    public String kakaoCallbackHandle(@RequestParam("code") String code,
                                      HttpSession session
    ) throws JsonProcessingException {
        // log.info("code = {}", code);
        KakaoTokenResponse response = kakaoApiService.exchangeToken(code);
        log.info("response.idToken = {}", response.getIdToken());

        DecodedJWT decodedJWT = JWT.decode(response.getIdToken());
        String sub = decodedJWT.getClaim("sub").asString();
        String nickname = decodedJWT.getClaim("nickname").asString();
        String picture = decodedJWT.getClaim("picture").asString();

        User found = userRepository.findByProviderAndProviderId("KAKAO", sub);
        log.info("found = {}", found);
        if (found != null) {
            session.setAttribute("user", found);
        } else {
            User user = User.builder().provider("KAKAO")
                    .providerId(sub).nickname(nickname).picture(picture).verified("T").build();
            userRepository.save(user);
            session.setAttribute("user", user);
        }

        return "redirect:/index";
    }

    @GetMapping("/find-password")
    public String findPasswordHandle(Model model) {
        return "auth/find-password";
    }

    @PostMapping("/find-password")
    public String findPasswordPostHandle(@ModelAttribute @Valid FindPasswordRequest req,
                                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("error", "이메일 형식이 잘못되었습니다");
            return "auth/find-password-error";

        }

        User found = userRepository.findByEmail(req.getEmail());
        if (found == null) {
            model.addAttribute("error", "해당이메일로 임시번호를 전송할수 없습니다");
            return "auth/find-password-error";
        }

        String temporalPassword = UUID.randomUUID().toString().substring(0,8);
        userRepository.updatePasswordByEmail(req.getEmail(), temporalPassword);
        mailService.sendTemporalPasswordMessage(req.getEmail(), temporalPassword);

        return "auth/find-password-success";
    }

    @GetMapping("/send-token")
    public String sendTokenHandle(@SessionAttribute("user") User user,
                                  Model model) {
        String token = UUID.randomUUID().toString().replace("-","");
        Verification one = Verification.builder()
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .userEmail(user.getEmail())
                .build();
        verificationRepository.save(one);
        mailService.sendVerificationMessage(user, one);

        return "auth/send-token";

    }

    @GetMapping("/email-verify")
    public String emailVerifyHandle(@RequestParam("token") String token, Model model) {
        Verification found = verificationRepository.findByToken(token);
        if (found == null) {
            model.addAttribute("error", "유효하지 않은 인증토큰 입니다.");
            return "auth/email-verify-error";
        }
        // found.getExpiresAt();   // 토큰이 가진 유효만료시점
        // LocalDateTime.now();    // 인증 시점
        if (LocalDateTime.now().isAfter(found.getExpiresAt())) {
            model.addAttribute("error", "유효기간이 만료된 인증토큰 입니다.");
            return "auth/email-verify-error";
        }

        String userEmail = found.getUserEmail();
        userRepository.updateVerifiedByEmail(userEmail);

        return "auth/email-verify-success";
    }

}
