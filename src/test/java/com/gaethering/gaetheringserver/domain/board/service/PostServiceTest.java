package com.gaethering.gaetheringserver.domain.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gaethering.gaetheringserver.domain.aws.s3.S3Service;
import com.gaethering.gaetheringserver.domain.board.dto.*;
import com.gaethering.gaetheringserver.domain.board.entity.Category;
import com.gaethering.gaetheringserver.domain.board.entity.Heart;
import com.gaethering.gaetheringserver.domain.board.entity.Post;
import com.gaethering.gaetheringserver.domain.board.entity.PostImage;
import com.gaethering.gaetheringserver.domain.board.exception.CategoryNotFoundException;
import com.gaethering.gaetheringserver.domain.board.exception.NoPermissionDeletePostException;
import com.gaethering.gaetheringserver.domain.board.exception.NoPermissionUpdatePostException;
import com.gaethering.gaetheringserver.domain.board.exception.PostNotFoundException;
import com.gaethering.gaetheringserver.domain.board.exception.errorCode.PostErrorCode;
import com.gaethering.gaetheringserver.domain.board.repository.CategoryRepository;
import com.gaethering.gaetheringserver.domain.board.repository.CommentRepository;
import com.gaethering.gaetheringserver.domain.board.repository.HeartRepository;
import com.gaethering.gaetheringserver.domain.board.repository.PostImageRepository;
import com.gaethering.gaetheringserver.domain.board.repository.PostRepository;
import com.gaethering.gaetheringserver.domain.member.entity.Member;
import com.gaethering.gaetheringserver.domain.member.exception.errorcode.MemberErrorCode;
import com.gaethering.gaetheringserver.domain.member.exception.member.MemberNotFoundException;
import com.gaethering.gaetheringserver.domain.member.repository.member.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;


@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private HeartRepository heartRepository;
    @Mock
    private PostImageRepository postImageRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private S3Service s3Service;
    @InjectMocks
    private PostServiceImpl postService;

    @Test
    @DisplayName("게시물 작성 실패 - 회원 찾을 수 없는 경우")
    void writePost_Fail_NoUser() {

        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.empty());

        PostWriteRequest request = PostWriteRequest.builder()
            .title("제목입니다")
            .content("내용입니다")
            .build();

        MemberNotFoundException exception = assertThrows(
            MemberNotFoundException.class,
            () -> postService.writePost(anyString(), 1L, null, request));

        assertEquals(MemberErrorCode.MEMBER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("게시물 작성 실패 - 카테고리 찾을 수 없는 경우")
    void writePost_Fail_NoCategory() {
        Member member = Member.builder()
            .id(1L)
            .email("email@gmail.com")
            .nickname("닉네임")
            .build();

        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member));

        given(categoryRepository.findById(anyLong()))
            .willReturn(Optional.empty());

        PostWriteRequest request = PostWriteRequest.builder()
            .title("제목입니다")
            .content("내용입니다")
            .build();

        CategoryNotFoundException exception = assertThrows(CategoryNotFoundException.class,
            () -> postService.writePost("test@gmail.com", anyLong(), null, request));

        assertEquals(PostErrorCode.CATEGORY_NOT_FOUND, exception.getPostErrorCode());
    }

    @Test
    @DisplayName("MultipartFile 리스트 ImgUrl 리스트로 변환")
    void changeMultipartFileList_toPostImageList() {

        List<MultipartFile> imageFiles = List.of(
            new MockMultipartFile("test1", "test1.PNG",
                MediaType.IMAGE_PNG_VALUE, "test1".getBytes(StandardCharsets.UTF_8))
        );

        given(s3Service.uploadImage(any(), anyString()))
            .willReturn(imageFiles.get(0).getName());

        List<String> imgUrlList = postService.getImageUrlsInRequest(imageFiles);
        assertEquals(1, imgUrlList.size());
        assertEquals("test1", imgUrlList.get(0));
    }

    @Test
    @DisplayName("게시물 작성 성공")
    void writePost_Success() {

        Member member = Member.builder()
            .id(1L)
            .email("email@gmail.com")
            .nickname("닉네임")
            .build();

        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member));

        Category category = Category.builder()
            .id(1L)
            .categoryName("카테고리")
            .build();

        given(categoryRepository.findById(anyLong()))
            .willReturn(Optional.of(category));

        PostWriteRequest request = PostWriteRequest.builder()
            .title("제목입니다")
            .content("내용입니다")
            .build();

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);

        PostWriteResponse response
            = postService.writePost(anyString(), 1L, new ArrayList<>(), request);

        assertEquals(0, response.getImageUrls().size());
        assertEquals("닉네임", response.getNickname());
        assertEquals("제목입니다", response.getTitle());
        verify(postRepository, times(1)).save(captor.capture());
    }

    @Test
    @DisplayName("게시글 수정 실패-회원 찾을 수 없는 경우")
    void updatePostFailure_MemberNotFound() {
        // given
        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.empty());

        PostUpdateRequest request = PostUpdateRequest.builder()
            .title("게시글 제목 수정")
            .content("게시글 내용 수정")
            .build();

        // when
        MemberNotFoundException exception = assertThrows(MemberNotFoundException.class,
            () -> postService.updatePost("test@test.com", anyLong(), request));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 수정 실패-게시물 찾을 수 없는 경우")
    void updatePostFailure_PostNotFound() {
        // given
        Member member = Member.builder()
            .id(1L)
            .email("gaethering@gmail.com")
            .build();
        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member));
        given(postRepository.findById(anyLong()))
            .willReturn(Optional.empty());

        PostUpdateRequest request = PostUpdateRequest.builder()
            .title("게시글 제목 수정")
            .content("게시글 내용 수정")
            .build();

        // when
        PostNotFoundException exception = assertThrows(PostNotFoundException.class,
            () -> postService.updatePost(member.getEmail(), 1L, request));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 수정 실패-게시물 작성자가 아닐 경우")
    void updatePostFailure_NoPermissionUpdatePost() {
        // given
        Member member1 = Member.builder()
            .id(1L)
            .email("gaethering@gmail.com")
            .build();
        Member member2 = Member.builder()
            .id(2L)
            .email("test@gmail.com")
            .build();
        Post post = Post.builder()
            .id(1L)
            .title("게시물 제목")
            .content("게시물 내용")
            .member(member1)
            .build();
        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member1));
        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member2));
        given(postRepository.findById(anyLong()))
            .willReturn(Optional.of(post));

        PostUpdateRequest request = PostUpdateRequest.builder()
            .title("게시글 제목 수정")
            .content("게시글 내용 수정")
            .build();

        // when
        NoPermissionUpdatePostException exception = assertThrows(NoPermissionUpdatePostException.class,
            () -> postService.updatePost(member2.getEmail(), 1L, request));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NO_PERMISSION_TO_UPDATE_POST);
    }

    @Test
    @DisplayName("게시글 수정 성공")
    void updatePostSuccess() {
        // given
        Member member1 = Member.builder()
            .id(1L)
            .email("gaethering@gmail.com")
            .nickname("닉네임")
            .build();
        Category category = Category.builder()
            .id(1L)
            .categoryName("질문 있어요")
            .build();
        PostImage postImage = PostImage.builder()
            .id(1L)
            .isRepresentative(false)
            .imageUrl("https://test~")
            .build();
        Post post = Post.builder()
            .id(1L)
            .title("게시물 제목")
            .content("게시물 내용")
            .member(member1)
            .postImages(List.of(postImage))
            .category(category)
            .build();

        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member1));
        given(postRepository.findById(anyLong()))
            .willReturn(Optional.of(post));
        given(heartRepository.countByPost(post))
            .willReturn(1L);

        PostUpdateRequest request = PostUpdateRequest.builder()
            .title("게시글 제목 수정")
            .content("게시글 내용 수정")
            .build();

        // when
        PostUpdateResponse response = postService.updatePost(member1.getEmail(), 1L, request);

        // then
        assertThat(response.getImageUrls().get(0).getImageId()).isEqualTo(postImage.getId());
        assertThat(response.getTitle()).isEqualTo(post.getTitle());
        assertThat(response.getContent()).isEqualTo(post.getContent());
        assertThat(response.getCreatedAt()).isEqualTo(post.getCreatedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(post.getUpdatedAt());
    }

    @Test
    @DisplayName("게시물 사진 업로드 실패_회원 존재하지 않음")
    void uploadPostImageFailure_MemberNotFound() {
        // given
        String filename = "test.txt";
        String contentType = "image/png";

        MockMultipartFile file = new MockMultipartFile("test", filename, contentType,
            "test".getBytes());

        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.empty());

        // when
        MemberNotFoundException exception = assertThrows(MemberNotFoundException.class,
            () -> postService.uploadPostImage("test@test.com", 1L, file));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("게시물 사진 업로드 실패_게시물 존재하지 않음")
    void uploadPostImageFailure_PostNotFound() {
        // given
        String filename = "test.txt";
        String contentType = "image/png";

        MockMultipartFile file = new MockMultipartFile("test", filename, contentType,
            "test".getBytes());
        Member member = Member.builder()
            .id(1L)
            .email("gaethering@gmail.com")
            .nickname("닉네임")
            .build();

        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member));
        given(postRepository.findById(anyLong()))
            .willReturn(Optional.empty());

        // when
        PostNotFoundException exception = assertThrows(PostNotFoundException.class,
            () -> postService.uploadPostImage("test@test.com", 1L, file));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시물 사진 업로드 실패_게시물 작성자가 아닌 경우")
    void uploadPostImageFailure_NoPermissionUpdatePost() {
        // given
        String filename = "test.txt";
        String contentType = "image/png";

        MockMultipartFile file = new MockMultipartFile("test", filename, contentType,
            "test".getBytes());

        Member member1 = Member.builder()
            .id(1L)
            .email("gaethering@gmail.com")
            .nickname("닉네임")
            .build();
        Member member2 = Member.builder()
            .id(2L)
            .email("gaethering@gmail.com")
            .nickname("닉네임")
            .build();
        Post post = Post.builder()
            .id(1L)
            .title("게시물 제목")
            .content("게시물 내용")
            .member(member1)
            .build();

        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member1));
        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member2));
        given(postRepository.findById(anyLong()))
            .willReturn(Optional.of(post));

        // when
        NoPermissionUpdatePostException exception = assertThrows(NoPermissionUpdatePostException.class,
            () -> postService.uploadPostImage(member2.getEmail(), 1L, file));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NO_PERMISSION_TO_UPDATE_POST);
    }

    @Test
    @DisplayName("게시물 사진 업로드 성공")
    void uploadPostImageSuccess() {
        // given
        String filename = "test.txt";
        String contentType = "image/png";

        MockMultipartFile file = new MockMultipartFile("test", filename, contentType,
            "test".getBytes());

        Member member1 = Member.builder()
            .id(1L)
            .email("gaethering@gmail.com")
            .nickname("닉네임")
            .build();

        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member1));

        Post post = Post.builder()
            .id(1L)
            .title("게시물 제목")
            .content("게시물 내용")
            .member(member1)
            .build();

        given(postRepository.findById(anyLong()))
            .willReturn(Optional.of(post));
        given(s3Service.uploadImage(any(), anyString()))
            .willReturn(file.getName());

        PostImage savedPostImage = PostImage.builder()
            .id(1L)
            .imageUrl("https://test~")
            .isRepresentative(false)
            .post(post)
            .build();

        // when
        when(postImageRepository.save(any(PostImage.class))).thenReturn(savedPostImage);
        PostImageUploadResponse response = postService.uploadPostImage(member1.getEmail(), 1L,
            file);

        // then
        assertThat(response.getImageId()).isEqualTo(savedPostImage.getId());
        assertThat(response.getImageUrl()).isEqualTo(savedPostImage.getImageUrl());
        assertThat(response.isRepresentative()).isEqualTo(savedPostImage.isRepresentative());
        assertThat(response.getCreatedAt()).isEqualTo(savedPostImage.getCreatedAt());
    }

    @Test
    @DisplayName("게시물 사진 삭제 실패_회원 존재하지 않음")
    void deletePostImageFailure_MemberNotFound() {
        // given
        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.empty());

        // when
        MemberNotFoundException exception = assertThrows(MemberNotFoundException.class,
            () -> postService.deletePostImage("test@test.com", 1L, 1L));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("게시물 사진 삭제 실패_게시물 존재하지 않음")
    void deletePostImageFailure_PostNotFound() {
        // given
        Member member = Member.builder()
            .id(1L)
            .email("gaethering@gmail.com")
            .nickname("닉네임")
            .build();

        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member));
        given(postRepository.findById(anyLong()))
            .willReturn(Optional.empty());

        // when
        PostNotFoundException exception = assertThrows(PostNotFoundException.class,
            () -> postService.deletePostImage("test@test.com", 1L, 1L));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시물 사진 삭제 실패_게시물 작성자가 아닌 경우")
    void deletePostImageFailure_NoPermissionUpdatePost() {
        // given
        Member member1 = Member.builder()
            .id(1L)
            .email("gaethering@gmail.com")
            .nickname("닉네임")
            .build();
        Member member2 = Member.builder()
            .id(2L)
            .email("gaethering@gmail.com")
            .nickname("닉네임")
            .build();
        Post post = Post.builder()
            .id(1L)
            .title("게시물 제목")
            .content("게시물 내용")
            .member(member1)
            .build();

        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member1));
        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member2));
        given(postRepository.findById(anyLong()))
            .willReturn(Optional.of(post));

        // when
        NoPermissionUpdatePostException exception = assertThrows(NoPermissionUpdatePostException.class,
            () -> postService.deletePostImage(member2.getEmail(), 1L, 1L));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NO_PERMISSION_TO_UPDATE_POST);
    }

    @Test
    @DisplayName("게시물 사진 삭제 성공")
    void deletePostImageSuccess() {
        // given
        Member member1 = Member.builder()
            .id(1L)
            .email("gaethering@gmail.com")
            .nickname("닉네임")
            .build();

        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member1));

        Post post = Post.builder()
            .id(1L)
            .title("게시물 제목")
            .content("게시물 내용")
            .member(member1)
            .build();

        given(postRepository.findById(anyLong()))
            .willReturn(Optional.of(post));

        PostImage postImage = PostImage.builder()
            .id(1L)
            .imageUrl("https://test~")
            .isRepresentative(false)
            .post(post)
            .build();

        given(postImageRepository.findById(anyLong()))
            .willReturn(Optional.of(postImage));

        // when
        boolean result = postService.deletePostImage(member1.getEmail(), 1L, 1L);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("게시글 삭제 실패-회원 찾을 수 없는 경우")
    void deletePostFailure_MemberNotFound() {
        // given
        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.empty());

        // when
        MemberNotFoundException exception = assertThrows(MemberNotFoundException.class,
            () -> postService.deletePost("test@test.com", anyLong()));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 삭제 실패-게시물 찾을 수 없는 경우")
    void deletePostFailure_PostNotFound() {
        // given
        Member member = Member.builder()
            .id(1L)
            .email("gaethering@gmail.com")
            .build();
        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member));
        given(postRepository.findById(anyLong()))
            .willReturn(Optional.empty());

        // when
        PostNotFoundException exception = assertThrows(PostNotFoundException.class,
            () -> postService.deletePost(anyString(), 1L));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 삭제 실패-게시물 작성자가 아닐 경우")
    void deletePostFailure_NoPermissionUpdatePost() {
        // given
        Member member1 = Member.builder()
            .id(1L)
            .email("gaethering@gmail.com")
            .build();
        Member member2 = Member.builder()
            .id(2L)
            .email("test@gmail.com")
            .build();
        Post post = Post.builder()
            .id(1L)
            .title("게시물 제목")
            .content("게시물 내용")
            .member(member1)
            .build();
        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member1));
        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member2));
        given(postRepository.findById(anyLong()))
            .willReturn(Optional.of(post));

        // when
        NoPermissionDeletePostException exception = assertThrows(NoPermissionDeletePostException.class,
            () -> postService.deletePost(member2.getEmail(), 1L));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NO_PERMISSION_TO_DELETE_POST);
    }

    @Test
    @DisplayName("게시글 삭제 성공")
    void deletePostSuccess() {
        // given
        Member member1 = Member.builder()
            .id(1L)
            .email("gaethering@gmail.com")
            .nickname("닉네임")
            .build();
        Category category = Category.builder()
            .id(1L)
            .categoryName("질문 있어요")
            .build();
        PostImage postImage = PostImage.builder()
            .id(1L)
            .isRepresentative(false)
            .imageUrl("https://test~")
            .build();
        Post post = Post.builder()
            .id(1L)
            .title("게시물 제목")
            .content("게시물 내용")
            .member(member1)
            .postImages(List.of(postImage))
            .category(category)
            .build();

        given(memberRepository.findByEmail(anyString()))
            .willReturn(Optional.of(member1));
        given(postRepository.findById(anyLong()))
            .willReturn(Optional.of(post));
        given(postImageRepository.findAllByPost(post))
            .willReturn(List.of(postImage));

        ArgumentCaptor<Post> captorPost = ArgumentCaptor.forClass(Post.class);

        // when
        boolean result = postService.deletePost(member1.getEmail(), 1L);

        // then
        verify(heartRepository).deleteHeartAllByPostId(eq(post.getId()));
        verify(commentRepository).deleteCommentsAllByPostId(eq(post.getId()));
        assertThat(result).isTrue();
        verify(postRepository, times(1)).delete(captorPost.capture());
    }

    @Test
    @DisplayName("게시물 목록 조회 성공")
    void getPosts_Success () {

        Category category = Category.builder()
                .id(1L)
                .categoryName("정보 공유")
                .build();

        given(categoryRepository.findById(anyLong()))
                .willReturn(Optional.of(category));

        Member member = Member.builder()
                .id(1L)
                .email("test@gmail.com")
                .build();

        given(memberRepository.findByEmail(anyString()))
                .willReturn(Optional.of(member));

        PostImage image1 = PostImage.builder()
                .id(1L)
                .imageUrl("https://test1")
                .isRepresentative(true)
                .build();

        PostImage image2 = PostImage.builder()
                .id(2L)
                .imageUrl("https://test2")
                .isRepresentative(false)
                .build();

        Post post1 = Post.builder()
                .id(1L)
                .title("제목1")
                .content("내용1")
                .category(category)
                .postImages(new ArrayList<>())
                .comments(new ArrayList<>())
                .hearts(new ArrayList<>())
                .build();

        post1.addImage(image1);

        Heart heart1 = Heart.builder()
                .member(member)
                .post(post1)
                .build();

        post1.pushPostHeart(heart1);

        given(postImageRepository.findByPostAndIsRepresentativeIsTrue(post1))
                .willReturn(Optional.of(image1));

        given(heartRepository.existsByPostAndMember(post1, member))
                .willReturn(true);

        Post post2 = Post.builder()
                .id(2L)
                .title("제목2")
                .content("내용2")
                .category(category)
                .postImages(new ArrayList<>())
                .comments(new ArrayList<>())
                .hearts(new ArrayList<>())
                .build();

        post2.addImage(image2);

        given(postImageRepository.findByPostAndIsRepresentativeIsTrue(post2))
                .willReturn(Optional.of(image2));

        Post post3 = Post.builder()
                .id(3L)
                .title("제목3")
                .content("내용3")
                .category(category)
                .postImages(new ArrayList<>())
                .comments(new ArrayList<>())
                .hearts(new ArrayList<>())
                .build();


        given(postRepository.findAllByCategoryAndIdIsLessThanOrderByIdDesc(any(Category.class), anyLong(), any(PageRequest.class)))
                .willReturn(List.of(post1, post2, post3));

        PostsGetResponse response = postService.getPosts( "test@gmail.com",1L, 5, 10);

        assertEquals(3, response.getPosts().size());
        assertEquals(post1.getComments().size(), response.getPosts().get(0).getCommentCnt());
        assertEquals(post1.getHearts().size(), response.getPosts().get(0).getHeartCnt());
        assertEquals(post1.getTitle(), response.getPosts().get(0).getTitle());
        assertEquals(post1.getContent(), response.getPosts().get(0).getContent());
        assertEquals(post1.getPostImages().get(0).getImageUrl(), response.getPosts().get(0).getImageUrl());
        assertEquals(true, response.getPosts().get(0).isHasHeart());
        assertEquals(null, response.getPosts().get(2).getImageUrl());
        assertEquals(false, response.getPosts().get(2).isHasHeart());
    }

    @Test
    @DisplayName("게시물 목록 조회 실패 - 카테고리 없음")
    void getPosts_Fail_NoCategory () {

        given(categoryRepository.findById(anyLong()))
                .willThrow(new CategoryNotFoundException());

        CategoryNotFoundException exception = assertThrows(CategoryNotFoundException.class,
                () -> postService.getPosts("test@gmail.com", 1L, 5, 10));

        assertEquals(PostErrorCode.CATEGORY_NOT_FOUND, exception.getPostErrorCode());
    }

    @Test
    @DisplayName("게시물 상세 조회 성공")
    void getOnePost_Success () {

        Member member = Member.builder()
                .id(1L)
                .email("test@gmail.com")
                .nickname("닉네임")
                .build();

        given(memberRepository.findByEmail(anyString()))
                .willReturn(Optional.of(member));

        Category category = Category.builder()
                .id(1L)
                .categoryName("정보 공유")
                .build();

        given(categoryRepository.existsById(anyLong()))
                .willReturn(true);

        Post post = Post.builder()
                .id(1L)
                .title("제목1")
                .content("내용1")
                .category(category)
                .member(member)
                .postImages(new ArrayList<>())
                .hearts(new ArrayList<>())
                .viewCnt(3)
                .build();

        given(postRepository.findById(anyLong()))
                .willReturn(Optional.of(post));

        Heart heart = Heart.builder()
                .post(post)
                .member(member)
                .build();

        post.pushPostHeart(heart);

        given(heartRepository.existsByPostAndMember(post, member))
                .willReturn(true);

        PostImage image1 = PostImage.builder()
                .isRepresentative(true)
                .post(post)
                .imageUrl("http://test1")
                .build();

        PostImage image2 = PostImage.builder()
                .isRepresentative(false)
                .post(post)
                .imageUrl("http://test1")
                .build();

        post.addImage(image1);
        post.addImage(image2);

        given(postImageRepository.findAllByPost(any(Post.class)))
                .willReturn(List.of(image1, image2));

        PostGetOneResponse response = postService.getOnePost(1L, "test123@gmail.com", 1L);

        assertEquals(post.getMember().getNickname(), response.getNickname());
        assertEquals(post.getPostImages().size(), response.getImages().size());
        assertEquals(false, response.isOwner());
        assertEquals(post.getHearts().size(), response.getHeartCnt());
        assertEquals(true, response.isHasHeart());
    }

    @Test
    @DisplayName("게시물 상세 조회 실패 - 게시물 없음")
    void getOnePost_Fail_NoPost () {

        Member member = Member.builder()
                .id(1L)
                .email("test@gmail.com")
                .nickname("닉네임")
                .build();

        given(memberRepository.findByEmail(anyString()))
                .willReturn(Optional.of(member));

        given(categoryRepository.existsById(anyLong()))
                .willReturn(true);

        given(postRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        PostNotFoundException exception = assertThrows(PostNotFoundException.class,
                () -> postService.getOnePost( 1L, "test@gmail.com",1L));

        assertEquals(PostErrorCode.POST_NOT_FOUND, exception.getPostErrorCode());
    }

    @Test
    @DisplayName("게시물 상세 조회 실패 - 카테고리 없음")
    void getOnePost_Fail_NoCategory () {

        Member member = Member.builder()
                .id(1L)
                .email("test@gmail.com")
                .nickname("닉네임")
                .build();

        given(memberRepository.findByEmail(anyString()))
                .willReturn(Optional.of(member));

        given(categoryRepository.existsById(anyLong()))
                .willReturn(false);

        CategoryNotFoundException exception = assertThrows(CategoryNotFoundException.class,
                () -> postService.getOnePost( 1L, "test@gmail.com",1L));

        assertEquals(PostErrorCode.CATEGORY_NOT_FOUND, exception.getPostErrorCode());
    }
}