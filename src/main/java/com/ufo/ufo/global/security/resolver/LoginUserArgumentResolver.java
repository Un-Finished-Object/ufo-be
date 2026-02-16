package com.ufo.ufo.global.security.resolver;

import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.UnauthorizedUserException;
import com.ufo.ufo.global.exception.UserNotFoundException;
import com.ufo.ufo.global.security.annotation.LoginUser;
import com.ufo.ufo.global.security.oauth.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean isLoginUserAnnotation = parameter.getParameterAnnotation(LoginUser.class) != null;
        boolean isUserClass = User.class.equals(parameter.getParameterType());
        return isLoginUserAnnotation && isUserClass;
    }

    @Override
    public @Nullable Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
                                            NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof CustomOAuth2User customOAuth2User) {
            return userRepository.findByEmail(customOAuth2User.getEmail())
                    .orElseThrow(UserNotFoundException::new);
        }

        String email = resolveEmailFromPrincipal(principal);

        if (email == null || email.isBlank()) {
            throw new UnauthorizedUserException();
        }

        return userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);
    }

    private @Nullable String resolveEmailFromPrincipal(Object principal) {
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof OAuth2User oAuth2User) {
            Object attrEmail = oAuth2User.getAttribute("email");
            if (attrEmail instanceof String emailValue) {
                return emailValue;
            }
            return oAuth2User.getName();
        }
        return null;
    }
}
