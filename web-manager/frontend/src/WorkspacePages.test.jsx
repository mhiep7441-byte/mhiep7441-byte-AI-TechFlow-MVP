// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { VideoFeedbackWidget, YouTubePublishModal } from './WorkspacePages';

const apiMock = vi.fn();
vi.mock('./api', () => ({ api: (...args) => apiMock(...args) }));

afterEach(cleanup);

describe('YouTubePublishModal', () => {
  it('requires explicit review consent before upload', () => {
    render(<YouTubePublishModal
      task={{ id: 7, title: 'Video đã kiểm chứng', caption: 'Caption', hashtags: '#AI' }}
      onClose={() => {}}
      onPublished={() => {}}
    />);
    const upload = screen.getByRole('button', { name: /xác nhận upload/i });
    expect(upload.disabled).toBe(true);
    fireEvent.click(screen.getByRole('checkbox', { name: /tôi đã xem video/i }));
    expect(upload.disabled).toBe(false);
  });
});

describe('VideoFeedbackWidget', () => {
  it('requires a rating and saves explicit user feedback', async () => {
    apiMock.mockRejectedValueOnce(new Error('Chưa có đánh giá')).mockResolvedValueOnce({});
    render(<VideoFeedbackWidget task={{ id: 12 }} />);

    const submit = screen.getByRole('button', { name: /gửi đánh giá/i });
    expect(submit.disabled).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: /5 sao/i }));
    fireEvent.click(submit);

    await waitFor(() => expect(apiMock).toHaveBeenLastCalledWith('/api/tasks/12/feedback', expect.objectContaining({
      method: 'PUT',
      body: expect.objectContaining({ rating: 5 }),
    })));
  });
});
