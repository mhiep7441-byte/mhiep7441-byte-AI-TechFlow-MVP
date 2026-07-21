# Agent rules

## Product goal

Tạo video ngắn tiếng Việt về công nghệ, lập trình và xu hướng.

## Safety boundaries

- Không tự đăng nếu chưa có bước review.
- Không chạy lệnh phá hủy dữ liệu.
- Không ghi API key vào source code hoặc log.
- Không sao chép nguyên văn bài báo.
- Nội dung thời sự phải có nguồn chính thức.
- Mọi video phải có trạng thái DRAFT_REQUIRES_REVIEW.

## Engineering rules

- Python 3.11+.
- Type hints cho hàm mới.
- Log lỗi có ngữ cảnh.
- Không bỏ qua lỗi subprocess.
- Mỗi tính năng mới cần có test tối thiểu.
- Giữ pipeline chạy được ở chế độ không có API key.
