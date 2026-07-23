// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { YouTubePublishModal } from './WorkspacePages';

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
