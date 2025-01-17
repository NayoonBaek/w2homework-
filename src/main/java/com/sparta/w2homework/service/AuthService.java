package com.sparta.w2homework.service;

import com.sparta.w2homework.dto.MemberRequestDto;
import com.sparta.w2homework.dto.MemberResponseDto;
import com.sparta.w2homework.dto.TokenDto;
import com.sparta.w2homework.dto.TokenRequestDto;
import com.sparta.w2homework.entity.Authority;
import com.sparta.w2homework.entity.Member;
import com.sparta.w2homework.entity.RefreshToken;
import com.sparta.w2homework.jwt.TokenProvider;
import com.sparta.w2homework.repository.MemberRepository;
import com.sparta.w2homework.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.coyote.http11.Constants.a;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;


//    public boolean registerUser(MemberRequestDto requestDto, Long id, String pw) {
//        Pattern username = Pattern.compile("^[A-Za-z[0-9]]{4,12}$");
//        String name = requestDto.getNickname();
//        Matcher matcher = username.matcher(name);
//
//        if (!(matcher.matches())){
//            return false;
//        }
//
//        Pattern password = Pattern.compile("^[a-z[0-9]]{4,32}$");
//        String passwords = requestDto.getPassword();
//        Matcher matchers = password.matcher(passwords);
//        if (!(matchers.matches())){
//            return false;
//        }
//
//        if (!(passwords.equals(requestDto.getPassword()))){
//            return false;
//        }
//
//
//// 패스워드 암호화
//        passwords = passwordEncoder.encode(passwords);
//        Authority authority = Authority.ROLE_ADMIN;
//
//        Member member = new Member(name, passwords, authority);
//        MemberRepository.save(member);
//
//        return true;
//    }

    @Transactional
    public MemberResponseDto signup(MemberRequestDto memberRequestDto) {
        //회원가입 (signup)
        //평범하게 유저 정보를 받아서 저장합니다.
        if (memberRepository.existsByNickname(memberRequestDto.getNickname())) {
            throw new RuntimeException("중복된 닉네임입니다.");
        }

        Member member = memberRequestDto.toMember(passwordEncoder);
        return MemberResponseDto.of(memberRepository.save(member));
    }

    @Transactional
    public TokenDto login(MemberRequestDto memberRequestDto) {
        // 1. Login ID/PW 를 기반으로 AuthenticationToken 생성
        UsernamePasswordAuthenticationToken authenticationToken = memberRequestDto.toAuthentication();

        // 2. 실제로 검증 (사용자 비밀번호 체크) 이 이루어지는 부분
        //    authenticate 메서드가 실행이 될 때 CustomUserDetailsService 에서 만들었던 loadUserByUsername 메서드가 실행됨
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        // 3. 인증 정보를 기반으로 JWT 토큰 생성
        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);

        // 4. RefreshToken 저장
        RefreshToken refreshToken = RefreshToken.builder()
                .key(authentication.getName())
                .value(tokenDto.getRefreshToken())
                .build();

        refreshTokenRepository.save(refreshToken);

        // 5. 토큰 발급
        return tokenDto;
    }

    @Transactional
    public TokenDto reissue(TokenRequestDto tokenRequestDto) {
        // 1. Refresh Token 검증
        //Refresh Token 의 만료 여부를 먼저 검사
        if (!tokenProvider.validateToken(tokenRequestDto.getRefreshToken())) {
            throw new RuntimeException("Token 이 유효하지 않습니다.");
        }

        // 2. Access Token 에서 Member ID 가져오기
        Authentication authentication = tokenProvider.getAuthentication(tokenRequestDto.getAccessToken());

        // 3. 저장소에서 Member ID 를 기반으로 Refresh Token 값 가져옴
        RefreshToken refreshToken = refreshTokenRepository.findByKey(authentication.getName())
                .orElseThrow(() -> new RuntimeException("로그아웃 된 사용자입니다."));

        // 4. Refresh Token 일치하는지 검사
        //저장소에 있는 Refresh Token 과 클라이언트가 전달한 Refresh Token 의 일치 여부를 검사합니다.
        if (!refreshToken.getValue().equals(tokenRequestDto.getRefreshToken())) {
            throw new RuntimeException("토큰의 유저 정보가 일치하지 않습니다.");
        }

        // 5. 새로운 토큰 생성
        //만약 일치한다면 로그인 했을 때와 동일하게 새로운 토큰을 생성해서 클라이언트에게 전달합니다.
        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);

        // 6. 저장소 정보 업데이트
        // Refresh Token 은 재사용하지 못하게 저장소에서 값을 갱신해줍니다.
        RefreshToken newRefreshToken = refreshToken.updateValue(tokenDto.getRefreshToken());
        refreshTokenRepository.save(newRefreshToken);

        // 토큰 발급
        return tokenDto;
    }
}