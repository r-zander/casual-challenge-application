/**
 * @typedef {object} SeasonDraftReport
 * @property {number} seasonNumber
 * @property {string} romanSeasonNumber
 * @property {string} startDate
 * @property {string} endDate
 * @property {string} finalsFriday
 * @property {string} nextSeasonStart
 * @property {string} preparedAt
 * @property {string | null} committedAt
 * @property {string | null} committedBy
 * @property {string | null} pullRequestUrl
 * @property {string} metaSource
 * @property {{cards: number, newBans: number, unbans: number}} counts
 * @property {MtgSet[]} setsReleased
 * @property {ScryfallDecks} scryfallDecks
 */

/**
 * @typedef {object} MtgSet
 * @property {string} name
 * @property {string} code
 * @property {number} newCardCount
 * @property {string[] | null} commanderDecks
 */

/**
 * @typedef {object} ScryfallDecks
 * @property {string} newBans
 * @property {string} unbans
 * @property {string} currentBans
 */

/**
 * @typedef {object} SeasonPreparationStatus
 * @property {string} state
 * @property {string} step
 * @property {number} stepNumber
 * @property {string | null} startedAt
 * @property {string | null} finishedAt
 * @property {string | null} errorMessage
 */

/**
 * @typedef {object} SeasonInfo
 * @property {number} seasonNumber
 * @property {string} startDate
 * @property {string} endDate
 * @property {string} updatedAt
 */

/**
 * @typedef {object} CommittedSeason
 * @property {number} seasonNumber
 * @property {string} romanSeasonNumber
 * @property {string} startDate
 * @property {string} finalsFriday
 * @property {string} endDate
 * @property {string} nextSeasonStart
 * @property {MtgSet[]} newSets
 * @property {{insertedCards: number, upsertedCardSeasonData: number, remappedCards: number, updatedCardNames: number}} counts
 * @property {{url: string} | null} pullRequest
 */

/**
 * @typedef {object} SeasonRemovalPreview
 * @property {number} seasonNumber
 * @property {number | null} previousSeasonNumber
 * @property {boolean} removable
 * @property {string | null} pullRequestUrl
 */

/**
 * @typedef {object} RemovedSeason
 * @property {number} seasonNumber
 * @property {number} previousSeasonNumber
 * @property {string} previousSeasonEndDate
 * @property {string | null} closedPullRequestUrl
 * @property {boolean} archiveDeleted
 * @property {{cardSeasonDataRows: number, deletedCards: number, undoneRenames: number, undoneRemaps: number}} counts
 */

/**
 * @typedef {object} TokenPayload
 * @property {string} sub
 * @property {boolean} admin
 * @property {number} exp
 */

export {};
