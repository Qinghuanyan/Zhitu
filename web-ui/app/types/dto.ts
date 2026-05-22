import type { TokenUsage } from "./core";
import type { UIMessageAnnotation } from "./annotations";
import type { UIMessagePart } from "./parts";

export interface ConversationListDto {
  id: string;
  assistantId: string;
  title: string;
  isPinned: boolean;
  createAt: number;
  updateAt: number;
  isGenerating: boolean;
}

export interface PagedResult<T> {
  items: T[];
  nextOffset?: number | null;
  hasMore: boolean;
}

export interface UploadedFileDto {
  id: number;
  url: string;
  fileName: string;
  mime: string;
  size: number;
}

export interface UploadFilesResponseDto {
  files: UploadedFileDto[];
}

export interface ConversationListInvalidateEventDto {
  type: "invalidate";
  assistantId: string;
  timestamp: number;
}

/**
 * Message DTO (for API response)
 * @see app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt - MessageDto
 */
export interface MessageDto {
  id: string;
  role: string;
  parts: UIMessagePart[];
  annotations?: UIMessageAnnotation[];
  createdAt: string;
  finishedAt?: string | null;
  modelId?: string | null;
  usage?: TokenUsage | null;
  translation?: string | null;
}

export interface MessageNodeDto {
  id: string;
  messages: MessageDto[];
  selectIndex: number;
}

export interface TravelPlanningBriefDto {
  destination: string;
  origin: string;
  dateRange: string;
  days?: number | null;
  travelerCount?: number | null;
  budgetLevel: string;
  budgetText: string;
  travelStyleTags: string[];
  transportPreferences: string[];
  hardConstraints: string[];
  userIntentSummary: string;
}

export interface TravelRecommendationItemDto {
  id: string;
  category: string;
  title: string;
  subtitle: string;
  tags: string[];
  reason: string;
  priceHint: string;
  ratingText: string;
  area: string;
  inventoryHint: string;
  bookingUrl: string;
  source: string;
  lat?: number | null;
  lon?: number | null;
  sourceMessageIds: string[];
}

export interface TravelPoiDto {
  id: string;
  name: string;
  category: string;
  lat?: number | null;
  lon?: number | null;
  address: string;
  linkedRecommendationId?: string | null;
  linkedItineraryItemId?: string | null;
}

export interface TravelItineraryItemDto {
  id: string;
  timeSlot: string;
  title: string;
  description: string;
  category: string;
  poiRefId?: string | null;
  estimatedCost: string;
  transportHint: string;
}

export interface TravelItineraryDayDto {
  dayIndex: number;
  title: string;
  dateText: string;
  weatherHint: string;
  items: TravelItineraryItemDto[];
}

export interface TravelPlanDto {
  conversationId: string;
  brief?: TravelPlanningBriefDto | null;
  hotels: TravelRecommendationItemDto[];
  foods: TravelRecommendationItemDto[];
  activities: TravelRecommendationItemDto[];
  pois: TravelPoiDto[];
  itineraryDays: TravelItineraryDayDto[];
  generatedAt?: number | null;
  generationVersion: number;
  status: string;
}

export interface ConversationDto {
  id: string;
  assistantId: string;
  title: string;
  messages: MessageNodeDto[];
  truncateIndex: number;
  chatSuggestions: string[];
  travelPlan?: TravelPlanDto | null;
  travelPlanningState?: string;
  isPinned: boolean;
  createAt: number;
  updateAt: number;
  isGenerating: boolean;
}

export interface ConversationSnapshotEventDto {
  type: "snapshot";
  seq: number;
  conversation: ConversationDto;
  serverTime: number;
}

export interface ConversationNodeUpdateEventDto {
  type: "node_update";
  seq: number;
  conversationId: string;
  nodeId: string;
  nodeIndex: number;
  node: MessageNodeDto;
  updateAt: number;
  isGenerating: boolean;
  serverTime: number;
}

export interface ConversationErrorEventDto {
  type: "error";
  message: string;
}

export interface MessageSearchResultDto {
  nodeId: string;
  messageId: string;
  conversationId: string;
  title: string;
  updateAt: number;
  snippet: string;
}
