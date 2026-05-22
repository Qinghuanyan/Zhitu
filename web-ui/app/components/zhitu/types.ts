export type ZhituShellTab =
  | "home"
  | "ai"
  | "map"
  | "itinerary"
  | "hotel"
  | "food"
  | "activity"
  | "profile";

export interface ZhituShellStat {
  label: string;
  value: string;
}

export interface ZhituShellUserProfile {
  name: string;
  subtitle?: string;
  stats?: ZhituShellStat[];
}

export interface ZhituShellMessage {
  id: string;
  role: string;
  text: string;
  createdAt?: string | null;
}

export interface ZhituShellSuggestion {
  id: string;
  name: string;
  subtitle?: string;
}

export interface ZhituShellRecommendation {
  id: string;
  title: string;
  subtitle?: string;
  tags?: string[];
  reason?: string;
  priceHint?: string;
  ratingText?: string;
  area?: string;
  inventoryHint?: string;
  bookingUrl?: string;
  lat?: number | null;
  lon?: number | null;
}

export interface ZhituShellPoi {
  id: string;
  name: string;
  category?: string;
  address?: string;
  lat?: number | null;
  lon?: number | null;
}

export interface ZhituShellItineraryItem {
  id: string;
  timeSlot?: string;
  title: string;
  description?: string;
  category?: string;
  estimatedCost?: string;
  transportHint?: string;
}

export interface ZhituShellItineraryDay {
  dayIndex: number;
  title: string;
  dateText?: string;
  weatherHint?: string;
  items: ZhituShellItineraryItem[];
}

export interface ZhituShellTravelBrief {
  destination?: string;
  origin?: string;
  dateRange?: string;
  days?: number | null;
  travelerCount?: number | null;
  budgetText?: string;
  budgetLevel?: string;
  travelStyleTags?: string[];
  transportPreferences?: string[];
  userIntentSummary?: string;
}

export interface ZhituShellTravelPlan {
  brief?: ZhituShellTravelBrief | null;
  hotels?: ZhituShellRecommendation[];
  foods?: ZhituShellRecommendation[];
  activities?: ZhituShellRecommendation[];
  pois?: ZhituShellPoi[];
  itineraryDays?: ZhituShellItineraryDay[];
  status?: string;
}

export interface ZhituShellConversation {
  id: string | null;
  title: string;
  isGenerating: boolean;
  suggestions?: string[];
  messages: ZhituShellMessage[];
}

export interface ZhituShellHistoryConversation {
  id: string;
  title: string;
  subtitle?: string;
  preview?: string;
  updatedAt?: string | null;
}

export interface ZhituShellHistoryTrip {
  id: string;
  conversationId: string;
  title: string;
  destination?: string;
  dateRange?: string;
  days?: number | null;
  summary?: string;
  updatedAt?: string | null;
  status?: string;
}

export interface ZhituShellFavoriteItem {
  id: string;
  title: string;
  subtitle?: string;
  category?: string;
  reason?: string;
  conversationId?: string | null;
  nodeId?: string | null;
}

export interface ZhituShellCurrentTripSummary {
  conversationId?: string | null;
  title: string;
  destination?: string;
  summary?: string;
  days?: number | null;
  dateRange?: string;
  status?: string;
}

export interface ZhituShellProfileUiState {
  activeTab?: "history" | "favorites";
}

export interface ZhituShellTravelUiState {
  searchQuery?: string;
  weatherSummary?: string;
  selectedMapFilter?: string;
  selectedDestination?: ZhituShellSuggestion | null;
  suggestions?: ZhituShellSuggestion[];
}

export interface ZhituShellAvailableActions {
  sendMessage?: boolean;
  generatePlan?: boolean;
  openMap?: boolean;
  openRecommendations?: boolean;
  openLegacyPanel?: boolean;
  exportConversation?: boolean;
}

export interface ZhituShellNavigationTargets {
  legacyPanel?: boolean;
  conversationList?: boolean;
  workbench?: boolean;
}

export interface ZhituShellStatePayload {
  version: 1;
  context: "web" | "android";
  currentTab: ZhituShellTab;
  conversation: ZhituShellConversation;
  travelPlan: ZhituShellTravelPlan | null;
  travelUiState: ZhituShellTravelUiState | null;
  user: ZhituShellUserProfile;
  historyConversations?: ZhituShellHistoryConversation[];
  historyTrips?: ZhituShellHistoryTrip[];
  favoriteItems?: ZhituShellFavoriteItem[];
  currentTripSummary?: ZhituShellCurrentTripSummary | null;
  profileUiState?: ZhituShellProfileUiState;
  availableActions?: ZhituShellAvailableActions;
  navigationTargets?: ZhituShellNavigationTargets;
}

export interface ZhituShellActionPayload {
  channel?: string;
  type?: string;
  action: string;
  payload?: Record<string, unknown> | null;
}
