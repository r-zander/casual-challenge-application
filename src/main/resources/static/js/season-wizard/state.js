/**
 * @typedef {import('./types.js').SeasonDraftReport} SeasonDraftReport
 * @typedef {import('./types.js').SeasonInfo} SeasonInfo
 * @typedef {import('./types.js').SeasonPreparationStatus} SeasonPreparationStatus
 * @typedef {import('./types.js').SeasonRemovalPreview} SeasonRemovalPreview
 */

export const TOKEN_STORAGE_KEY = 'casual-challenge-admin-token';
export const DONE_STEPS_STORAGE_KEY = 'casual-challenge-done-steps';
export const COMMIT_RESULT_STORAGE_KEY = 'casual-challenge-commit-result';
export const OPEN_STEP_STORAGE_KEY = 'casual-challenge-open-step';
export const SQL_PARTS = ['00_add_season', '01_insert_cards', '02_insert_card_season_data'];
export const LAST_STEP = 7;

/**
 * @typedef {object} WizardState
 * @property {string | null} adminToken
 * @property {SeasonDraftReport | null} draftReport
 * @property {SeasonInfo | null} currentSeason
 * @property {SeasonPreparationStatus | null} preparationStatus
 * @property {number | null} pollTimer
 * @property {boolean} isStopping
 * @property {boolean} hasBlockingSanityFailure
 * @property {string[]} downloadedParts
 * @property {number[]} doneSteps
 * @property {SeasonRemovalPreview | null} removalPreview
 */

/** @type {WizardState} */
export const state = {
    adminToken: null,
    draftReport: null,
    currentSeason: null,
    preparationStatus: null,
    pollTimer: null,
    isStopping: false,
    hasBlockingSanityFailure: false,
    downloadedParts: [],
    doneSteps: [],
    removalPreview: null
};
