package com.gaethering.gaetheringserver.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaethering.gaetheringserver.domain.board.dto.CommentDetailResponse;
import com.gaethering.gaetheringserver.domain.board.dto.CommentRequest;
import com.gaethering.gaetheringserver.domain.board.dto.CommentResponse;
import com.gaethering.gaetheringserver.domain.board.dto.CommentsGetResponse;
import com.gaethering.gaetheringserver.domain.board.exception.CommentNotFoundException;
import com.gaethering.gaetheringserver.domain.board.exception.NoPermissionDeleteCommentException;
import com.gaethering.gaetheringserver.domain.board.exception.NoPermissionUpdateCommentException;
import com.gaethering.gaetheringserver.domain.board.exception.PostNotFoundException;
import com.gaethering.gaetheringserver.domain.board.service.CommentService;
import com.gaethering.gaetheringserver.domain.member.exception.member.MemberNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.gaethering.gaetheringserver.domain.board.exception.errorCode.PostErrorCode.*;
import static com.gaethering.gaetheringserver.domain.member.exception.errorcode.MemberErrorCode.MEMBER_NOT_FOUND;
import static com.gaethering.gaetheringserver.member.util.ApiDocumentUtils.getDocumentRequest;
import static com.gaethering.gaetheringserver.member.util.ApiDocumentUtils.getDocumentResponse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class CommentControllerTest {

    @MockBean
    private CommentService commentService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("댓글 작성 성공")
    @WithMockUser
    void writeComment_Success () throws Exception {

        CommentRequest request = CommentRequest.builder()
                .content("댓글입니다")
                .build();

        LocalDateTime date = LocalDateTime.of(2022, 12, 31, 23, 59, 59);

        CommentResponse response = CommentResponse.builder()
                .commentId(1L)
                .memberId(1L)
                .content("댓글입니다")
                .nickname("닉네임")
                .createdAt(date)
                .build();

        when(commentService.writeComment(anyString(), anyLong(), any(CommentRequest.class)))
                .thenReturn(response);

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/boards/{postId}/comments", 1L)
                        .content(requestJson)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.commentId").value(String.valueOf(response.getCommentId())))
                .andExpect(jsonPath("$.memberId").value(String.valueOf(response.getMemberId())))
                .andExpect(jsonPath("$.content").value(response.getContent()))
                .andExpect(jsonPath("$.nickname").value(response.getNickname()))
                .andExpect(jsonPath("$.createdAt").value(String.valueOf(response.getCreatedAt())))
                .andExpect(status().isCreated())
                .andDo(print())
                .andDo(document("boards/comments/write-comment/success",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("댓글 작성하고자 하는 게시물 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }
    @Test
    @DisplayName("댓글 작성 실패 - 회원 없음")
    @WithMockUser
    void write_Comment_fail_NoUser () throws Exception {

        CommentRequest request = CommentRequest.builder()
                .content("댓글입니다")
                .build();

        given(commentService.writeComment(anyString(), anyLong(), any(CommentRequest.class)))
                .willThrow(new MemberNotFoundException());

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/boards/{postId}/comments", 1L)
                        .content(requestJson)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.code").value(MEMBER_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(MEMBER_NOT_FOUND.getMessage()))
                .andExpect(status().is4xxClientError())
                .andDo(print())
                .andDo(document("boards/comments/write-comment/failure/member-not-found",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("댓글 작성하고자 하는 게시물 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }
    @Test
    @DisplayName("댓글 작성 실패 - 게시물 없음")
    @WithMockUser
    void write_Comment_fail_NoPost () throws Exception {

        CommentRequest request = CommentRequest.builder()
                .content("댓글입니다")
                .build();

        given(commentService.writeComment(anyString(), anyLong(), any(CommentRequest.class)))
                .willThrow(new PostNotFoundException());

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/boards/{postId}/comments", 1L)
                        .content(requestJson)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.code").value(POST_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(POST_NOT_FOUND.getMessage()))
                .andExpect(status().is4xxClientError())
                .andDo(print())
                .andDo(document("boards/comments/write-comment/failure/post-not-found",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("댓글 작성하고자 하는 게시물 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }

    @Test
    @DisplayName("댓글 삭제 성공")
    @WithMockUser
    void deleteComment_Success () throws Exception {

        when(commentService.deleteComment(anyString(), anyLong(), anyLong()))
                .thenReturn(true);

        mockMvc.perform(delete("/api/boards/{postId}/comments/{commentId}", 1L, 1L)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(status().isOk())
                .andDo(print())
                .andDo(document("boards/comments/delete-comment/success",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("삭제하고자 하는 댓글의 게시물 id"),
                                parameterWithName("commentId").description("삭제하고자 하는 댓글 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }

    @Test
    @DisplayName("댓글 삭제 실패 - 회원 없음")
    @WithMockUser
    void delete_Comment_fail_NoUser () throws Exception {

        given(commentService.deleteComment(anyString(), anyLong(), anyLong()))
                .willThrow(new MemberNotFoundException());

        mockMvc.perform(delete("/api/boards/{postId}/comments/{commentId}", 1L, 1L)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.code").value(MEMBER_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(MEMBER_NOT_FOUND.getMessage()))
                .andExpect(status().is4xxClientError())
                .andDo(print())
                .andDo(document("boards/comments/delete-comment/failure/member-not-found",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("삭제하고자 하는 댓글의 게시물 id"),
                                parameterWithName("commentId").description("삭제하고자 하는 댓글 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }

    @Test
    @DisplayName("댓글 삭제 실패 - 게시물 없음")
    @WithMockUser
    void delete_Comment_fail_NoPost () throws Exception {

        given(commentService.deleteComment(anyString(), anyLong(), anyLong()))
                .willThrow(new PostNotFoundException());

        mockMvc.perform(delete("/api/boards/{postId}/comments/{commentId}", 1L, 1L)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.code").value(POST_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(POST_NOT_FOUND.getMessage()))
                .andExpect(status().is4xxClientError())
                .andDo(print())
                .andDo(document("boards/comments/delete-comment/failure/post-not-found",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("삭제하고자 하는 댓글의 게시물 id"),
                                parameterWithName("commentId").description("삭제하고자 하는 댓글 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }

    @Test
    @DisplayName("댓글 삭제 실패 - 댓글 없음")
    @WithMockUser
    void delete_Comment_fail_NoComment () throws Exception {

        given(commentService.deleteComment(anyString(), anyLong(), anyLong()))
                .willThrow(new CommentNotFoundException());

        mockMvc.perform(delete("/api/boards/{postId}/comments/{commentId}", 1L, 1L)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.code").value(COMMENT_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(COMMENT_NOT_FOUND.getMessage()))
                .andExpect(status().is4xxClientError())
                .andDo(print())
                .andDo(document("boards/comments/delete-comment/failure/comment-not-found",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("삭제하고자 하는 댓글의 게시물 id"),
                                parameterWithName("commentId").description("삭제하고자 하는 댓글 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }

    @Test
    @DisplayName("댓글 삭제 실패 - 삭제 권한 없음")
    @WithMockUser
    void delete_Comment_fail_UNMATCH_writer () throws Exception {

        given(commentService.deleteComment(anyString(), anyLong(), anyLong()))
                .willThrow(new NoPermissionDeleteCommentException());

        mockMvc.perform(delete("/api/boards/{postId}/comments/{commentId}", 1L, 1L)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.code").value(NO_PERMISSION_TO_DELETE_COMMENT.getCode()))
                .andExpect(jsonPath("$.message").value(NO_PERMISSION_TO_DELETE_COMMENT.getMessage()))
                .andExpect(status().is4xxClientError())
                .andDo(print())
                .andDo(document("boards/comments/delete-comment/failure/un-match-writer",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("삭제하고자 하는 댓글의 게시물 id"),
                                parameterWithName("commentId").description("삭제하고자 하는 댓글 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }

    @Test
    @DisplayName("댓글 수정 성공")
    @WithMockUser
    void updateComment_Success () throws Exception {

        CommentRequest request = CommentRequest.builder()
                .content("수정 댓글입니다")
                .build();

        LocalDateTime date = LocalDateTime.of(2022, 12, 31, 23, 59, 59);

        CommentResponse response = CommentResponse.builder()
                .commentId(1L)
                .memberId(1L)
                .content("수정 댓글입니다")
                .nickname("닉네임")
                .createdAt(date)
                .build();

        when(commentService.updateComment(anyString(), anyLong(), anyLong(), any(CommentRequest.class)))
                .thenReturn(response);

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(put("/api/boards/{postId}/comments/{commentId}", 1L, 1L)
                        .content(requestJson)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.commentId").value(String.valueOf(response.getCommentId())))
                .andExpect(jsonPath("$.memberId").value(String.valueOf(response.getMemberId())))
                .andExpect(jsonPath("$.content").value(response.getContent()))
                .andExpect(jsonPath("$.nickname").value(response.getNickname()))
                .andExpect(jsonPath("$.createdAt").value(String.valueOf(response.getCreatedAt())))
                .andExpect(status().isOk())
                .andDo(print())
                .andDo(document("boards/comments/update-comment/success",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("수정하고자 하는 댓글의 게시물 id"),
                                parameterWithName("commentId").description("수정하고자 하는 댓글 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }

    @Test
    @DisplayName("댓글 수정 실패 - 회원 없음")
    @WithMockUser
    void update_Comment_fail_NoUser () throws Exception {

        CommentRequest request = CommentRequest.builder()
                .content("댓글입니다")
                .build();

        given(commentService.updateComment(anyString(), anyLong(), anyLong(), any(CommentRequest.class)))
                .willThrow(new MemberNotFoundException());

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(put("/api/boards/{postId}/comments/{commentId}", 1L, 1L)
                        .content(requestJson)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.code").value(MEMBER_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(MEMBER_NOT_FOUND.getMessage()))
                .andExpect(status().is4xxClientError())
                .andDo(print())
                .andDo(document("boards/comments/update-comment/failure/member-not-found",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("수정하고자 하는 댓글의 게시물 id"),
                                parameterWithName("commentId").description("수정하고자 하는 댓글 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }

    @Test
    @DisplayName("댓글 수정 실패 - 게시물 없음")
    @WithMockUser
    void update_Comment_fail_NoPost () throws Exception {

        CommentRequest request = CommentRequest.builder()
                .content("댓글입니다")
                .build();

        given(commentService.updateComment(anyString(), anyLong(), anyLong(), any(CommentRequest.class)))
                .willThrow(new PostNotFoundException());

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(put("/api/boards/{postId}/comments/{commentId}", 1L, 1L)
                        .content(requestJson)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.code").value(POST_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(POST_NOT_FOUND.getMessage()))
                .andExpect(status().is4xxClientError())
                .andDo(print())
                .andDo(document("boards/comments/update-comment/failure/post-not-found",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("수정하고자 하는 댓글의 게시물 id"),
                                parameterWithName("commentId").description("수정하고자 하는 댓글 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }

    @Test
    @DisplayName("댓글 수정 실패 - 댓글 없음")
    @WithMockUser
    void update_Comment_fail_NoComment () throws Exception {

        CommentRequest request = CommentRequest.builder()
                .content("댓글입니다")
                .build();

        given(commentService.updateComment(anyString(), anyLong(), anyLong(), any(CommentRequest.class)))
                .willThrow(new CommentNotFoundException());

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(put("/api/boards/{postId}/comments/{commentId}", 1L, 1L)
                        .content(requestJson)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.code").value(COMMENT_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(COMMENT_NOT_FOUND.getMessage()))
                .andExpect(status().is4xxClientError())
                .andDo(print())
                .andDo(document("boards/comments/update-comment/failure/comment-not-found",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("수정하고자 하는 댓글의 게시물 id"),
                                parameterWithName("commentId").description("수정하고자 하는 댓글 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }

    @Test
    @DisplayName("댓글 수정 실패 - 수정 권한 없음")
    @WithMockUser
    void update_Comment_fail_UNMATCH_writer () throws Exception {

        CommentRequest request = CommentRequest.builder()
                .content("댓글입니다")
                .build();

        given(commentService.updateComment(anyString(), anyLong(), anyLong(), any(CommentRequest.class)))
                .willThrow(new NoPermissionUpdateCommentException());

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(put("/api/boards/{postId}/comments/{commentId}", 1L, 1L)
                        .content(requestJson)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.code").value(NO_PERMISSION_TO_UPDATE_COMMENT.getCode()))
                .andExpect(jsonPath("$.message").value(NO_PERMISSION_TO_UPDATE_COMMENT.getMessage()))
                .andExpect(status().is4xxClientError())
                .andDo(print())
                .andDo(document("boards/comments/update-comment/failure/un-match-writer",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("수정하고자 하는 댓글의 게시물 id"),
                                parameterWithName("commentId").description("수정하고자 하는 댓글 id")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }

    @Test
    @DisplayName("댓글 조회 성공")
    @WithMockUser
    void getCommentsByPost_Success () throws Exception {

        LocalDateTime date1 = LocalDateTime.of(2022, 12, 31, 19, 59, 59);
        LocalDateTime date2 = LocalDateTime.of(2022, 12, 31, 21, 59, 59);
        LocalDateTime date3 = LocalDateTime.of(2022, 12, 31, 22, 59, 59);
        LocalDateTime date4 = LocalDateTime.of(2022, 12, 31, 23, 59, 59);


        CommentDetailResponse comment1 = CommentDetailResponse.builder()
                .commentId(1L)
                .memberId(1L)
                .content("댓글입니다1")
                .owner(true)
                .nickname("닉네임1")
                .createdAt(date1)
                .build();

        CommentDetailResponse comment2 = CommentDetailResponse.builder()
                .commentId(2L)
                .memberId(3L)
                .content("댓글입니다2")
                .owner(false)
                .nickname("닉네임2")
                .createdAt(date2)
                .build();

        CommentDetailResponse comment3 = CommentDetailResponse.builder()
                .commentId(3L)
                .memberId(1L)
                .content("댓글입니다3")
                .owner(true)
                .nickname("닉네임3")
                .createdAt(date3)
                .build();

        CommentDetailResponse comment4 = CommentDetailResponse.builder()
                .commentId(4L)
                .memberId(5L)
                .content("댓글입니다4")
                .owner(false)
                .nickname("닉네임4")
                .createdAt(date4)
                .build();

        List<CommentDetailResponse> commentResponses = List.of(comment4, comment3, comment2, comment1);

        CommentsGetResponse response = CommentsGetResponse.builder()
                .comments(commentResponses)
                .totalCommentsCnt(4)
                .nextCursor(-1)
                .build();

        when(commentService.getCommentsByPost(anyString(), anyLong(), anyInt(), anyLong()))
                .thenReturn(response);

        mockMvc.perform(get("/api/boards/{postId}/comments", 1L)
                        .param("size", "5")
                        .param("lastCommentId", "9223372036854775807")
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.comments[0].commentId").value(comment4.getCommentId()))
                .andExpect(jsonPath("$.comments[0].memberId").value(comment4.getMemberId()))
                .andExpect(jsonPath("$.comments[0].content").value(comment4.getContent()))
                .andExpect(jsonPath("$.comments[0].nickname").value(comment4.getNickname()))
                .andExpect(jsonPath("$.comments[0].createdAt").value(comment4.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                .andExpect(jsonPath("$.comments[0].isOwner").value(comment4.isOwner()))
                .andExpect(jsonPath("$.comments[1].commentId").value(comment3.getCommentId()))
                .andExpect(jsonPath("$.comments[1].memberId").value(comment3.getMemberId()))
                .andExpect(jsonPath("$.comments[1].content").value(comment3.getContent()))
                .andExpect(jsonPath("$.comments[1].nickname").value(comment3.getNickname()))
                .andExpect(jsonPath("$.comments[1].createdAt").value(comment3.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                .andExpect(jsonPath("$.comments[1].isOwner").value(comment3.isOwner()))
                .andExpect(jsonPath("$.comments[2].commentId").value(comment2.getCommentId()))
                .andExpect(jsonPath("$.comments[2].memberId").value(comment2.getMemberId()))
                .andExpect(jsonPath("$.comments[2].content").value(comment2.getContent()))
                .andExpect(jsonPath("$.comments[2].nickname").value(comment2.getNickname()))
                .andExpect(jsonPath("$.comments[2].createdAt").value(comment2.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                .andExpect(jsonPath("$.comments[2].isOwner").value(comment2.isOwner()))
                .andExpect(jsonPath("$.comments[3].commentId").value(comment1.getCommentId()))
                .andExpect(jsonPath("$.comments[3].memberId").value(comment1.getMemberId()))
                .andExpect(jsonPath("$.comments[3].content").value(comment1.getContent()))
                .andExpect(jsonPath("$.comments[3].nickname").value(comment1.getNickname()))
                .andExpect(jsonPath("$.comments[3].createdAt").value(comment1.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                .andExpect(jsonPath("$.comments[3].isOwner").value(comment1.isOwner()))
                .andExpect(jsonPath("$.totalCommentsCnt").value(response.getTotalCommentsCnt()))
                .andExpect(jsonPath("$.nextCursor").value(response.getNextCursor()))
                .andExpect(status().isOk())
                .andDo(print())
                .andDo(document("boards/comments/get-comments/success",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("댓글들을 조회하고자 하는 게시물 id")),
                        requestParameters(parameterWithName("size").description("한 번에 보여줄 댓글의 개수"),
                                parameterWithName("lastCommentId").description("한 번에 읽은 댓글들의 가장 마지막 댓글의 Id - 처음 조회할 경우 Long 타입의 최대값")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }

    @Test
    @DisplayName("댓글 조회 실패 - 게시물 없음")
    @WithMockUser
    void getCommentsByPost_Fail_NoPost () throws Exception {

        given(commentService.getCommentsByPost(anyString(), anyLong(), anyInt(), anyLong()))
                .willThrow(new PostNotFoundException());

        mockMvc.perform(get("/api/boards/{postId}/comments", 1L)
                        .param("size", "5")
                        .param("lastCommentId", "9223372036854775807")
                        .header("Authorization", "accessToken"))
                .andExpect(jsonPath("$.code").value(POST_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(POST_NOT_FOUND.getMessage()))
                .andExpect(status().is4xxClientError())
                .andDo(print())
                .andDo(document("boards/comments/get-comments/failure/post-not-found",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(parameterWithName("postId").description("댓글들을 조회하고자 하는 게시물 id")),
                        requestParameters(parameterWithName("size").description("한 번에 보여줄 댓글의 개수"),
                                parameterWithName("lastCommentId").description("한 번에 읽은 댓글들의 가장 마지막 댓글의 Id - 처음 조회할 경우 Long 타입의 최대값")),
                        requestHeaders(
                                headerWithName("Authorization").description("Access Token"))
                ));
    }
}