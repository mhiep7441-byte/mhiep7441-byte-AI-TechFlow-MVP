import { describe, expect, it } from 'vitest';
import { evidenceSummary, safeJson } from './utils';

describe('safeJson', () => {
  it('returns parsed objects and never throws on malformed worker metadata', () => {
    expect(safeJson('{"sources":[]}')).toEqual({ sources: [] });
    expect(safeJson('{not-json', { safe: true })).toEqual({ safe: true });
    expect(safeJson('null', {})).toEqual({});
  });
});

describe('evidenceSummary', () => {
  it('normalises research and storyboard data for Video Studio', () => {
    const result = evidenceSummary({
      researchJson: '{"sources":[{"id":"S1","url":"https://example.com"}]}',
      storyboardJson: '{"scenes":[{"title":"HOOK"}]}',
      factCheckStatus: 'VERIFIED',
      qualityScore: 91,
    });

    expect(result.sources).toHaveLength(1);
    expect(result.scenes[0].title).toBe('HOOK');
    expect(result.verified).toBe(true);
    expect(result.qualityScore).toBe(91);
  });
});
