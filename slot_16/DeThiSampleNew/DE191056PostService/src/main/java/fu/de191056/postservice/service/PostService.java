package fu.de191056.postservice.service;

import fu.de191056.postservice.dto.ApiResponse;
import fu.de191056.postservice.dto.PostDTO;
import org.springframework.data.domain.Pageable;

public interface PostService {
    ApiResponse createPost(PostDTO dto);
    ApiResponse updatePost(Long postId, PostDTO dto);
    ApiResponse getPostById(Long postId);
    ApiResponse cancelPost(Long postId);
    ApiResponse getPostList(String advertiserName, String status, Pageable pageable);
}
