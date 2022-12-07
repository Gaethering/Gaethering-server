package com.gaethering.gaetheringserver.member.exception.auth;


import com.gaethering.gaetheringserver.member.exception.errorcode.MemberErrorCode;

public class TokenNotExistUserInfoException extends MemberAuthException {

    public TokenNotExistUserInfoException(MemberErrorCode memberErrorCode) {
        super(memberErrorCode);
    }
}
