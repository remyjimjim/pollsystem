// Enums
export enum AccessLevel {
  VIEWER = 'VIEWER',
  USER = 'USER',
  CREATOR = 'CREATOR',
  ADMIN = 'ADMIN',
  SUPER = 'SUPER'
}

export enum RequestStatus {
  PENDING = 'PENDING',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED'
}

export enum PollStatus {
  DRAFT = 'DRAFT',
  PUBLISHED = 'PUBLISHED',
  CLOSED = 'CLOSED',
  ARCHIVED = 'ARCHIVED'
}

// Core Types
export interface User {
  id: number
  email: string
  phone: string
  zipcode: string
  access: AccessLevel
  isEnabled: boolean
  paidUntil: string | null
}

export interface RoleAssignment {
  id: number
  userId: number
  role: AccessLevel
  pollTypeId: number | null
  stateId: number
  countyId: number
  zipcode: string
  enabled: boolean
  assignedAt: string
}

export interface CreatorRequest {
  id: number
  userId: number
  assignedAdminId: number | null
  status: RequestStatus
  reason: string
  submittedAt: string
  processedAt: string | null
}

// Geographic Types
export interface State {
  id: number
  name: string
  initial: string
}

export interface County {
  id: number
  stateId: number
  name: string
}

export interface CountyZip {
  id: number
  countyId: number
  zipcode: string
}

// Poll Types
export interface PollType {
  id: number
  pollType: number
  name: string
}

export interface Office {
  id: number
  desc: string
  dateCreated: string
  lastUpdated: string
}

// Election Types
export interface Election {
  id: number
  creatorId: number
  pollTypeId: number
  title: string
  date: string
  zipcode: string
  status: PollStatus
  closeDate: string | null
  dateSubmitted: string
}

export interface Candidate {
  id: number
  name: string
  affiliation: string
  officeId: number
  electionId: number
  createDate: string
}

export interface CandidateResponse {
  id: number
  userId: number
  candidateId: number
  response: boolean
  comment: string | null
  dateSubmitted: string
  lastModified: string | null
}

// Ballot Measure Types
export interface BallotMeasure {
  id: number
  creatorId: number
  pollTypeId: number
  title: string
  summary: string
  electionId: number
  effectiveDate: string
  status: PollStatus
  closeDate: string | null
  dateCreated: string
  lastUpdated: string
}

export interface BallotResponse {
  id: number
  measureId: number
  userId: number
  response: boolean
  comment: string | null
  dateSubmitted: string
  lastModified: string | null
}

// Questionnaire Types
export interface Questionnaire {
  id: number
  creatorId: number
  pollTypeId: number
  title: string
  summary: string
  status: PollStatus
  closeDate: string | null
  createDate: string
  submitDate: string | null
}

export interface QuestionnaireDomain {
  id: number
  questionnaireId: number
  stateId: number
  countyId: number
  zipcode: string
}

export interface Question {
  id: number
  questionnaireId: number
  question: string
}

export interface QuestionResponse {
  id: number
  questionId: number
  response: string
  userId: number
  comment: string | null
  dateSubmitted: string
  lastModified: string | null
}

// Search / Filter Types
export interface PollSearchFilters {
  zipcode?: string
  candidateName?: string
  referendumTitle?: string
  pollTitle?: string
  creatorName?: string
}

// Auth Types — magic-link sign-in (no passwords)
// phone + zipcode are only required when registering a new email.
export interface MagicLinkRequest {
  email: string
  phone?: string
  zipcode?: string
}

export interface MagicLinkRedeemRequest {
  token: string
}

export interface AuthResponse {
  token: string
  user: User
}
