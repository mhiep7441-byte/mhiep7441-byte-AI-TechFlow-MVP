// @vitest-environment jsdom
import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TikTokPublishModal } from './App';
import { api } from './api';

vi.mock('./api', () => ({ api: vi.fn() }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('TikTokPublishModal', () => {
  it('requires explicit consent and uses creator privacy options before posting', async () => {
    api
      .mockResolvedValueOnce({
        username: 'nolovenolife137',
        nickname: 'No Love No Life',
        privacyLevelOptions: ['SELF_ONLY'],
        commentDisabled: false,
        duetDisabled: true,
        stitchDisabled: false,
        maxVideoPostDurationSec: 60,
      })
      .mockResolvedValueOnce({ publishId: 'publish-1', message: 'TikTok đã nhận video' });
    const published = vi.fn();

    render(<TikTokPublishModal
      task={{ id: 7, title: 'AI Agent', caption: 'AI Agent có kiểm chứng', hashtags: '#AI' }}
      onClose={vi.fn()}
      onPublished={published}
    />);

    expect(await screen.findByText('@nolovenolife137')).toBeTruthy();
    const submit = screen.getByRole('button', { name: /Duyệt & gửi TikTok/i });
    expect(submit.disabled).toBe(true);

    fireEvent.click(screen.getByRole('checkbox', { name: /Tôi đã xem video/i }));
    expect(submit.disabled).toBe(false);
    fireEvent.click(submit);

    await waitFor(() => expect(api).toHaveBeenLastCalledWith('/api/tasks/7/publish/tiktok', expect.objectContaining({
      method: 'POST',
      body: expect.objectContaining({ consent: true, privacyLevel: 'SELF_ONLY' }),
    })));
    await waitFor(() => expect(published).toHaveBeenCalledWith(expect.objectContaining({ publishId: 'publish-1' })));
  });
});
