/* Copy this file to config.js (and public/js/config.js) and fill in your values.
 * config.js is gitignored — never commit real keys. */
"use strict";
const SUPABASE = {
  url: "https://YOUR_PROJECT.supabase.co",
  anonKey: "YOUR_SUPABASE_ANON_KEY"
};

/* GitHub Releases — the app checks these for updates. Create a classic
 * personal access token (repo scope) at github.com/settings/tokens and
 * paste it here. Never commit config.js. */
const GITHUB = {
  token: "YOUR_GITHUB_TOKEN",
  repoOwner: "psb-001",
  repoName: "paperadda",
};

/* Fixed taxonomy (mirrors the Android app). COMMON = First Year (all branches). */
const BRANCHES = [
  { code: 'COMMON', fullName: 'First Year (All Branches)' },
  { code: 'ENTC', fullName: 'Electronics and Telecommunication Engineering' },
  { code: 'AIML', fullName: 'Artificial Intelligence and Machine Learning' },
  { code: 'CE', fullName: 'Computer Engineering' },
  { code: 'IT', fullName: 'Information Technology' },
];
const YEARS = [
  { year: 1, label: 'First Year' },
  { year: 2, label: 'Second Year' },
  { year: 3, label: 'Third Year' },
  { year: 4, label: 'Final Year' },
];
const yearLabel = (y) => (YEARS.find((t) => t.year === y) || {}).label || ('Year ' + y);
const branchName = (c) => (BRANCHES.find((b) => b.code === c) || {}).fullName || c;
