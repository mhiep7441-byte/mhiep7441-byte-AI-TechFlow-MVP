export function safeJson(value, fallback = {}) {
  if (!value) return fallback;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' ? parsed : fallback;
  } catch {
    return fallback;
  }
}

export function evidenceSummary(task) {
  const research = safeJson(task?.researchJson);
  const storyboard = safeJson(task?.storyboardJson);
  return {
    research,
    storyboard,
    sources: Array.isArray(research.sources) ? research.sources : [],
    scenes: Array.isArray(storyboard.scenes) ? storyboard.scenes : [],
    verified: task?.factCheckStatus === 'VERIFIED',
    qualityScore: Number.isInteger(task?.qualityScore) ? task.qualityScore : null,
  };
}
