(function () {
    const runtimeUi = {
        profileOpen: false,
        itineraryOpen: false,
        recommendationDetail: null,
    };

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatText(value) {
        return escapeHtml(value).replace(/\n/g, '<br>');
    }

    function dataAttrs(action, payload) {
        const attrs = [`data-zhitu-action="${escapeHtml(action)}"`];
        Object.entries(payload || {}).forEach(([key, value]) => {
            if (value == null || value === '') return;
            const attrName = key.replace(/[A-Z]/g, (char) => '-' + char.toLowerCase());
            attrs.push(`data-${attrName}="${escapeHtml(String(value))}"`);
        });
        return attrs.join(' ');
    }

    function getState() {
        return window.__ZHITU_STATE__ || null;
    }

    function readState(input) {
        if (!input) return null;
        if (typeof input === 'string') {
            try {
                return JSON.parse(input);
            } catch (error) {
                console.error('failed to parse zhitu state', error);
                return null;
            }
        }
        return input;
    }

    function postAction(action, payload) {
        if (!window.RikkaZhituBridge || typeof window.RikkaZhituBridge.postMessage !== 'function') {
            return false;
        }
        window.RikkaZhituBridge.postMessage(JSON.stringify({
            type: 'action',
            action,
            payload: payload || {},
        }));
        return true;
    }

    function reportRuntime(level, message) {
        try {
            if (console && typeof console[level] === 'function') {
                console[level](`[zhitu-runtime] ${message}`);
            } else if (console && typeof console.log === 'function') {
                console.log(`[zhitu-runtime][${level}] ${message}`);
            }
        } catch (error) {
            console.log(error);
        }
    }

    function setOverlayVisible(id, visible) {
        const node = document.getElementById(id);
        if (!node) return;
        node.classList.toggle('hidden', !visible);
        node.style.display = visible ? 'flex' : '';
    }

    function scrollMainToBottom() {
        const container = document.getElementById('chat-container');
        if (!container) return;
        requestAnimationFrame(() => {
            container.scrollTop = container.scrollHeight;
        });
    }

    function stripPreviewArtifacts() {
        const body = document.body;
        if (!body) return;

        const textNodeType = typeof Node === 'undefined' ? 3 : Node.TEXT_NODE;
        const elementNodeType = typeof Node === 'undefined' ? 1 : Node.ELEMENT_NODE;
        const childNodes = body.childNodes ? Array.from(body.childNodes) : [];

        childNodes.forEach((node) => {
            if (node.nodeType === textNodeType && String(node.textContent || '').trim()) {
                node.remove();
                return;
            }
            if (node.nodeType !== elementNodeType) return;
            const element = node;
            const tagName = String(element.tagName || '').toUpperCase();
            if (element.id === 'app-container' || tagName === 'SCRIPT' || tagName === 'STYLE') {
                return;
            }
            element.remove();
        });

        const allElements = document.querySelectorAll ? document.querySelectorAll('body *') : [];
        Array.from(allElements || []).forEach((element) => {
            const text = String(element.textContent || '').trim();
            if (!text) return;
            if (
                text.includes('开发者模式') ||
                text.includes('[开发者模式]') ||
                text.includes('寮€鍙戣€呮')
            ) {
                element.style.display = 'none';
            }
        });
    }

    function normalizeStaticLayout() {
        const body = document.body;
        const app = document.getElementById('app-container');

        if (body && body.style) {
            body.style.margin = '0';
            body.style.minHeight = '100dvh';
            body.style.backgroundColor = '#FAFAFA';
            body.style.display = 'block';
            body.style.fontFamily = "'Inter', 'PingFang SC', 'Microsoft YaHei', sans-serif";
            body.style.overflow = 'hidden';
        }

        if (document.documentElement && document.documentElement.style) {
            document.documentElement.style.backgroundColor = '#FAFAFA';
        }

        if (app && app.style) {
            app.style.position = 'fixed';
            app.style.inset = '0';
            app.style.width = '100vw';
            app.style.height = '100dvh';
            app.style.maxWidth = '100vw';
            app.style.maxHeight = '100dvh';
            app.style.border = 'none';
            app.style.borderRadius = '0';
            app.style.boxShadow = 'none';
            app.style.backgroundColor = '#FAFAFA';
            app.style.zIndex = '1';
        }

        const header = document.querySelector('header');
        if (header && header.style) {
            header.style.paddingTop = 'calc(env(safe-area-inset-top, 0px) + 2rem)';
            header.style.position = 'relative';
            header.style.zIndex = '2';
        }

        const chatContainer = document.getElementById('chat-container');
        if (chatContainer && chatContainer.style) {
            chatContainer.style.paddingBottom = '11rem';
        }

        const input = document.getElementById('chat-input');
        if (input) {
            if (typeof input.setAttribute === 'function') {
                input.setAttribute('placeholder', '输入你的目的地、日期或偏好...');
            } else {
                input.placeholder = '输入你的目的地、日期或偏好...';
            }
        }

        [
            ['itinerary', '行程'],
            ['hotel', '住宿'],
            ['food', '餐饮'],
            ['activity', '活动'],
        ].forEach(([tab, label]) => {
            const span = document.querySelector(`.quick-tab[data-tab="${tab}"] span`);
            if (span) span.textContent = label;
        });

        const mapBadge = document.querySelector('#map-fullscreen .absolute.top-4.left-4 span');
        if (mapBadge) mapBadge.textContent = '地图模式 · 实时点位';

        const hotelName = document.getElementById('sheet-hotel-name');
        if (hotelName && !hotelName.dataset.zhituBound) {
            hotelName.textContent = '住宿详情';
        }
        const hotelPrice = document.getElementById('sheet-hotel-price');
        if (hotelPrice && !hotelPrice.dataset.zhituBound) {
            hotelPrice.textContent = '待更新';
        }
        const favoriteLabel = document.getElementById('hotel-fav-label');
        if (favoriteLabel) favoriteLabel.textContent = '收藏';

        const itineraryClose = document.querySelector('#itinerary-full-overlay h2');
        if (itineraryClose && !itineraryClose.dataset.zhituBound) {
            itineraryClose.textContent = '行程详情';
        }
        const itineraryHint = document.querySelector('#itinerary-full-overlay .glass-panel p');
        if (itineraryHint && !itineraryHint.dataset.zhituBound) {
            itineraryHint.textContent = '当前行程会由原生规划结果实时注入。';
        }

        const preferenceTitle = document.querySelector('#preference-overlay h3');
        if (preferenceTitle) preferenceTitle.textContent = '偏好设置';
        const preferenceHint = document.querySelector('#preference-overlay .px-5.pt-2.pb-4 p');
        if (preferenceHint) preferenceHint.textContent = '这些偏好会用于生成更贴近需求的推荐内容。';
        const preferenceSave = document.getElementById('preference-save-btn');
        if (preferenceSave) preferenceSave.textContent = '保存偏好设置';

        const profileTitle = document.querySelector('#profile-overlay h2');
        if (profileTitle) profileTitle.textContent = '我的资料';

        let topMask = document.getElementById('zhitu-top-mask');
        if (!topMask && body && typeof document.createElement === 'function') {
            topMask = document.createElement('div');
            topMask.id = 'zhitu-top-mask';
            if (typeof body.appendChild === 'function') {
                body.appendChild(topMask);
            }
        }
        if (topMask && topMask.style) {
            topMask.style.position = 'fixed';
            topMask.style.top = '0';
            topMask.style.left = '0';
            topMask.style.right = '0';
            topMask.style.height = 'calc(env(safe-area-inset-top, 0px) + 30px)';
            topMask.style.background = '#FAFAFA';
            topMask.style.pointerEvents = 'none';
            topMask.style.zIndex = '2147483647';
        }
    }

    function renderEmptyState(title, subtitle, buttons) {
        return `
            <div class="bg-white rounded-3xl border border-gray-100 shadow-sm p-5 animate-fade-in">
                <div class="w-11 h-11 rounded-2xl bg-origami-gradient flex items-center justify-center text-white shadow-neon-green mb-4">
                    <i data-lucide="sparkles" class="w-5 h-5"></i>
                </div>
                <h3 class="text-base font-bold text-gray-900">${escapeHtml(title)}</h3>
                <p class="text-sm text-gray-500 leading-relaxed mt-2">${escapeHtml(subtitle)}</p>
                <div class="flex flex-wrap gap-2 mt-4">${buttons || ''}</div>
            </div>
        `;
    }

    function renderSuggestionChips(items, action, fieldName) {
        return (items || []).map((item) => {
            const value = typeof item === 'string' ? item : item && (item[fieldName] || item.name || item.title);
            if (!value) return '';
            const payload = {};
            payload[fieldName] = value;
            return `<button ${dataAttrs(action, payload)} class="px-3 py-1.5 rounded-full bg-white border border-gray-200 text-xs font-semibold text-gray-600 hover:border-brand-green/40 hover:text-brand-green transition-colors">${escapeHtml(value)}</button>`;
        }).join('');
    }

    function renderConversationMessage(message) {
        const role = String(message && message.role || '').toLowerCase();
        const isUser = role === 'user';
        const bubbleClass = isUser ? 'chat-bubble-user text-white ml-auto' : 'chat-bubble-ai text-gray-700';
        const wrapperClass = isUser ? 'justify-end' : 'justify-start';
        const avatar = isUser ? '' : `
            <div class="w-8 h-8 rounded-full bg-origami-gradient p-[1px] flex-shrink-0 mt-1">
                <div class="w-full h-full bg-white rounded-full overflow-hidden">
                    <img src="https://images.unsplash.com/photo-1620641788421-7a1c342ea42e?q=80&w=100&auto=format&fit=crop" class="w-full h-full object-cover" alt="AI">
                </div>
            </div>
        `;
        return `
            <div class="flex ${wrapperClass} gap-2 w-full animate-slide-up">
                ${avatar}
                <div class="${bubbleClass} px-4 py-3 max-w-[86%] text-sm leading-relaxed break-words">
                    ${formatText(message && message.text || '')}
                </div>
            </div>
        `;
    }

    function renderTripSummaryCard(state) {
        const plan = state.travelPlan;
        if (!plan || !plan.brief) return '';
        const brief = plan.brief;
        const tags = (brief.travelStyleTags || []).slice(0, 4).map((tag) =>
            `<span class="px-2 py-1 rounded-full bg-brand-light text-brand-green text-[10px] font-semibold">${escapeHtml(tag)}</span>`
        ).join('');
        return `
            <div class="pl-10 pr-2 animate-slide-up">
                <div class="bg-white rounded-3xl border border-brand-green/15 shadow-sm p-4">
                    <div class="flex items-start justify-between gap-3">
                        <div class="min-w-0 flex-1">
                            <p class="text-[10px] font-bold uppercase tracking-wider text-brand-green">当前行程</p>
                            <h3 class="text-base font-bold text-gray-900 mt-1">${escapeHtml(brief.destination || state.conversation.title || '当前行程')}</h3>
                            <p class="text-xs text-gray-500 mt-1">${escapeHtml(brief.dateRange || brief.userIntentSummary || '当前行程数据已与原生规划结果同步。')}</p>
                        </div>
                        <div class="text-right flex-shrink-0">
                            <p class="text-sm font-bold text-brand-purple">${escapeHtml(brief.budgetText || (((brief.days || (plan.itineraryDays || []).length || 0)) + ' 天'))}</p>
                            <p class="text-[10px] text-gray-400 mt-1">${escapeHtml(plan.status || '')}</p>
                        </div>
                    </div>
                    ${tags ? `<div class="flex flex-wrap gap-2 mt-3">${tags}</div>` : ''}
                    <div class="flex gap-2 mt-4">
                        <button ${dataAttrs('open_itinerary', {})} class="flex-1 py-2.5 rounded-2xl bg-brand-light text-brand-green text-xs font-bold hover:bg-brand-green hover:text-white transition-colors">查看行程</button>
                        <button ${dataAttrs('open_map', { filter: state.travelUiState && state.travelUiState.selectedMapFilter || '' })} class="flex-1 py-2.5 rounded-2xl bg-brand-purple/10 text-brand-purple text-xs font-bold hover:bg-brand-purple hover:text-white transition-colors">地图</button>
                    </div>
                </div>
            </div>
        `;
    }

    function renderRecommendationSortChips(rec) {
        return (rec.sortOptions || []).map((option) => {
            const activeClass = option.active
                ? 'bg-origami-gradient text-white border-transparent shadow-neon-green'
                : 'bg-white text-gray-600 border-gray-200';
            return `<button ${dataAttrs('sort_recommendations', { category: rec.category, sort: option.key })} class="px-3 py-1.5 rounded-full border text-xs font-semibold transition-colors ${activeClass}">${escapeHtml(option.label || option.key)}</button>`;
        }).join('');
    }

    function renderAssistantRecommendationCard(item, category, expanded) {
        const tags = (item.tags || []).slice(0, 5).map((tag) =>
            `<span class="px-2 py-1 rounded-full bg-gray-100 text-gray-500 text-[10px] font-semibold">${escapeHtml(tag)}</span>`
        ).join('');
        const detail = expanded ? `
            <div class="mt-4 pt-4 border-t border-gray-100 space-y-3">
                ${item.reason ? `<p class="text-sm text-gray-600 leading-relaxed">${escapeHtml(item.reason)}</p>` : ''}
                <div class="grid grid-cols-2 gap-2 text-[11px] text-gray-500">
                    ${item.priceHint ? `<div class="rounded-xl bg-gray-50 px-3 py-2"><span class="block text-gray-400">价格</span><span class="block mt-1 text-gray-700">${escapeHtml(item.priceHint)}</span></div>` : ''}
                    ${item.ratingText ? `<div class="rounded-xl bg-gray-50 px-3 py-2"><span class="block text-gray-400">评分</span><span class="block mt-1 text-gray-700">${escapeHtml(item.ratingText)}</span></div>` : ''}
                    ${item.area ? `<div class="rounded-xl bg-gray-50 px-3 py-2"><span class="block text-gray-400">区域</span><span class="block mt-1 text-gray-700">${escapeHtml(item.area)}</span></div>` : ''}
                    ${item.distanceText ? `<div class="rounded-xl bg-gray-50 px-3 py-2"><span class="block text-gray-400">距离</span><span class="block mt-1 text-gray-700">${escapeHtml(item.distanceText)}</span></div>` : ''}
                    ${item.inventoryHint ? `<div class="rounded-xl bg-gray-50 px-3 py-2 col-span-2"><span class="block text-gray-400">提示</span><span class="block mt-1 text-gray-700">${escapeHtml(item.inventoryHint)}</span></div>` : ''}
                </div>
                <div class="flex gap-2">
                    <button ${dataAttrs('open_map', { filter: category, poiId: item.id || '' })} class="flex-1 py-2.5 rounded-2xl bg-brand-light text-brand-green text-xs font-bold hover:bg-brand-green hover:text-white transition-colors">地图</button>
                    <button ${dataAttrs('request_recommendations', { category })} class="flex-1 py-2.5 rounded-2xl bg-brand-purple/10 text-brand-purple text-xs font-bold hover:bg-brand-purple hover:text-white transition-colors">刷新推荐</button>
                </div>
            </div>
        ` : '';
        return `
            <div class="bg-white rounded-3xl border border-gray-100 shadow-sm p-4 animate-slide-up">
                <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0 flex-1">
                        <h3 class="text-base font-bold text-gray-900">${escapeHtml(item.title || '推荐项')}</h3>
                        <p class="text-xs text-gray-500 mt-1">${escapeHtml(item.subtitle || item.area || '')}</p>
                    </div>
                    <div class="text-right flex-shrink-0">
                        ${item.priceHint ? `<p class="text-sm font-bold text-brand-purple">${escapeHtml(item.priceHint)}</p>` : ''}
                        ${item.ratingText ? `<p class="text-[11px] text-yellow-500 mt-1">${escapeHtml(item.ratingText)}</p>` : ''}
                        ${item.distanceText ? `<p class="text-[10px] text-gray-400 mt-1">${escapeHtml(item.distanceText)}</p>` : ''}
                    </div>
                </div>
                ${tags ? `<div class="flex flex-wrap gap-2 mt-3">${tags}</div>` : ''}
                <div class="flex gap-2 mt-4">
                    <button ${dataAttrs('toggle_recommendation_detail', { category, itemId: item.id || '' })} class="flex-1 py-2.5 rounded-2xl bg-gray-100 text-gray-700 text-xs font-bold hover:bg-gray-200 transition-colors">${expanded ? '收起详情' : '查看详情'}</button>
                </div>
                ${detail}
            </div>
        `;
    }

    function renderAssistantRecommendation(state, rec) {
        if (!rec) return '';
        const expandedKey = runtimeUi.recommendationDetail && runtimeUi.recommendationDetail.category === rec.category
            ? runtimeUi.recommendationDetail.itemId
            : null;
        const content = (rec.items || []).length
            ? (rec.items || []).map((item) => renderAssistantRecommendationCard(item, rec.category, expandedKey === item.id)).join('')
            : renderEmptyState(
                rec.emptyTitle || '暂无推荐',
                rec.emptyDescription || '当前暂无可用推荐数据。',
                `<button ${dataAttrs('request_recommendations', { category: rec.category })} class="px-4 py-2.5 rounded-2xl bg-origami-gradient text-white text-xs font-bold shadow-neon-green">重试</button>`
            );
        return `
            <div class="pl-10 pr-2 space-y-3 animate-slide-up">
                <div class="bg-white rounded-3xl border border-brand-green/15 shadow-sm p-4">
                    <div class="flex items-start justify-between gap-3">
                        <div class="min-w-0 flex-1">
                            <p class="text-[10px] font-bold uppercase tracking-wider text-brand-green">对话推荐</p>
                            <h3 class="text-base font-bold text-gray-900 mt-1">${escapeHtml(rec.title || '推荐结果')}</h3>
                        </div>
                        <span class="px-2 py-1 rounded-full bg-brand-light text-brand-green text-[10px] font-semibold">${escapeHtml(rec.category || '')}</span>
                    </div>
                    <div class="flex flex-wrap gap-2 mt-4">${renderRecommendationSortChips(rec)}</div>
                </div>
                ${content}
            </div>
        `;
    }

    function renderConversation(container, state) {
        const conversation = state.conversation || {};
        const messages = conversation.messages || [];
        const ui = state.travelUiState || {};
        const assistantRecommendation = state.assistantRecommendation || null;
        let html = '';

        if (!messages.length) {
            const destinationName = (ui.selectedDestination && ui.selectedDestination.name)
                || (state.travelPlan && state.travelPlan.brief && state.travelPlan.brief.destination)
                || '告诉我你想去哪、什么时候出发';
            const queryText = ui.searchQuery
                || '输入目的地、日期、预算或偏好，原生规划结果会实时回流到当前页面。';
            html += `
                <div class="bg-white rounded-3xl border border-gray-100 shadow-sm p-5 animate-fade-in">
                    <div class="flex items-start gap-3">
                        <div class="w-11 h-11 rounded-2xl bg-origami-gradient flex items-center justify-center text-white shadow-neon-green flex-shrink-0">
                            <i data-lucide="compass" class="w-5 h-5"></i>
                        </div>
                        <div class="min-w-0 flex-1">
                            <p class="text-[10px] font-bold uppercase tracking-wider text-brand-green">AI 行程规划</p>
                            <h3 class="text-base font-bold text-gray-900 mt-1">${escapeHtml(destinationName)}</h3>
                            <p class="text-sm text-gray-500 leading-relaxed mt-2">${escapeHtml(queryText)}</p>
                            <div class="mt-4 rounded-2xl bg-gray-50 border border-gray-100 px-4 py-3">
                                <p class="text-[11px] font-semibold text-gray-700">天气</p>
                                <p class="text-[11px] text-gray-500 mt-1">${escapeHtml(ui.weatherSummary || '完成目的地同步后，这里会显示天气与出行建议。')}</p>
                            </div>
                        </div>
                    </div>
                    ${ui.suggestions && ui.suggestions.length ? `<div class="mt-4"><p class="text-[11px] font-semibold text-gray-700 mb-2">目的地建议</p><div class="flex flex-wrap gap-2">${renderSuggestionChips(ui.suggestions, 'select_destination', 'name')}</div></div>` : ''}
                    ${conversation.suggestions && conversation.suggestions.length ? `<div class="mt-4"><p class="text-[11px] font-semibold text-gray-700 mb-2">快捷问题</p><div class="flex flex-wrap gap-2">${renderSuggestionChips(conversation.suggestions, 'send_message', 'text')}</div></div>` : ''}
                    <div class="flex gap-2 mt-4">
                        <button ${dataAttrs('generate_plan', {})} class="flex-1 py-2.5 rounded-2xl bg-origami-gradient text-white text-xs font-bold shadow-neon-green">生成行程</button>
                        <button ${dataAttrs('open_map', { filter: ui.selectedMapFilter || '' })} class="flex-1 py-2.5 rounded-2xl bg-gray-100 text-gray-700 text-xs font-bold hover:bg-gray-200 transition-colors">地图</button>
                    </div>
                </div>
            `;
        } else {
            html += messages.map(renderConversationMessage).join('');
            if (conversation.isGenerating) {
                html += `
                    <div class="flex items-start gap-2 w-full animate-fade-in">
                        <div class="w-8 h-8 rounded-full bg-origami-gradient p-[1px] flex-shrink-0 mt-1">
                            <div class="w-full h-full bg-white rounded-full overflow-hidden">
                                <img src="https://images.unsplash.com/photo-1620641788421-7a1c342ea42e?q=80&w=100&auto=format&fit=crop" class="w-full h-full object-cover" alt="AI">
                            </div>
                        </div>
                        <div class="chat-bubble-ai px-4 py-4 max-w-[80%] flex items-center gap-1">
                            <div class="w-1.5 h-1.5 bg-brand-green rounded-full animate-bounce" style="animation-delay:0ms"></div>
                            <div class="w-1.5 h-1.5 bg-brand-green rounded-full animate-bounce" style="animation-delay:150ms"></div>
                            <div class="w-1.5 h-1.5 bg-brand-green rounded-full animate-bounce" style="animation-delay:300ms"></div>
                        </div>
                    </div>
                `;
            }
            html += renderTripSummaryCard(state);
            html += renderAssistantRecommendation(state, assistantRecommendation);
        }

        if (!messages.length && assistantRecommendation) {
            html += `<div class="mt-4">${renderAssistantRecommendation(state, assistantRecommendation)}</div>`;
        }

        container.innerHTML = html;
        scrollMainToBottom();
    }

    function renderItineraryDay(day) {
        const items = (day.items || []).map((item) => `
            <div class="flex gap-3 items-start">
                <span class="text-[10px] text-gray-400 w-12 flex-shrink-0 mt-0.5">${escapeHtml(item.timeSlot || '待定')}</span>
                <div class="flex-1 bg-gray-50 rounded-xl px-3 py-2.5">
                    <p class="text-xs font-semibold text-gray-800">${escapeHtml(item.title || '未命名行程项')}</p>
                    ${item.description ? `<p class="text-[10px] text-gray-500 mt-1 leading-relaxed">${escapeHtml(item.description)}</p>` : ''}
                </div>
            </div>
        `).join('');
        return `<div class="bg-white rounded-2xl p-4 border border-gray-100 shadow-sm"><h4 class="text-sm font-bold text-gray-900 mb-2">${escapeHtml(day.title || '行程日')}</h4><p class="text-[10px] text-gray-400 mb-3">${escapeHtml(day.dateText || day.weatherHint || '')}</p><div class="space-y-2.5">${items || '<p class="text-xs text-gray-400">暂无行程项</p>'}</div></div>`;
    }

    function renderItineraryOverlay(state) {
        const overlay = document.getElementById('itinerary-full-overlay');
        if (!overlay) return;
        const titleEl = overlay.querySelector('h2');
        const subtitleEl = overlay.querySelector('p');
        const body = overlay.querySelector('.flex-1.overflow-y-auto');
        const plan = state.travelPlan || {};
        const brief = plan.brief || {};
        const days = plan.itineraryDays || [];
        if (titleEl) titleEl.textContent = brief.destination || state.conversation.title || '行程详情';
        if (subtitleEl) subtitleEl.textContent = brief.dateRange || brief.userIntentSummary || '当前行程详情';
        if (!body) return;
        body.innerHTML = days.length
            ? `${days.map(renderItineraryDay).join('')}<div class="flex gap-2 pt-4"><button ${dataAttrs('export_itinerary', {})} class="flex-1 py-3 rounded-2xl bg-brand-light text-brand-green text-xs font-semibold">导出 Markdown</button><button ${dataAttrs('share_itinerary', {})} class="flex-1 py-3 rounded-2xl bg-brand-purple/10 text-brand-purple text-xs font-semibold">分享行程</button></div>`
            : renderEmptyState('暂无行程', '请先生成行程，完整的日程安排会显示在这里。', `<button ${dataAttrs('generate_plan', {})} class="px-4 py-2.5 rounded-2xl bg-origami-gradient text-white text-xs font-bold shadow-neon-green">生成行程</button>`);
    }

    function renderItinerary(container, state) {
        const plan = state.travelPlan || {};
        const brief = plan.brief || {};
        const days = plan.itineraryDays || [];
        container.innerHTML = days.length
            ? `<div class="bg-white rounded-3xl border border-gray-100 shadow-sm p-5 animate-fade-in"><h3 class="text-lg font-bold text-gray-900">${escapeHtml(brief.destination || state.conversation.title || '行程')}</h3><p class="text-sm text-gray-500 mt-2">${escapeHtml(brief.userIntentSummary || brief.dateRange || '下方内容已与原生行程规划结果同步。')}</p><div class="flex gap-2 mt-4"><button data-zhitu-local="open-itinerary-overlay" class="flex-1 py-2.5 rounded-2xl bg-brand-light text-brand-green text-xs font-bold">查看完整行程</button><button ${dataAttrs('replan_trip', { conversationId: state.currentTripSummary && state.currentTripSummary.conversationId || state.conversation.id })} class="flex-1 py-2.5 rounded-2xl bg-brand-purple/10 text-brand-purple text-xs font-bold">重新规划</button></div></div><div class="space-y-4 mt-4">${days.map(renderItineraryDay).join('')}</div>`
            : renderEmptyState('暂无行程', '继续对话或生成计划后，这里会展示完整行程。', `<button ${dataAttrs('generate_plan', {})} class="px-4 py-2.5 rounded-2xl bg-origami-gradient text-white text-xs font-bold shadow-neon-green">生成行程</button>`);
        renderItineraryOverlay(state);
    }

    function renderMap(container, state) {
        const pois = (state.travelPlan && state.travelPlan.pois) || [];
        const filter = state.travelUiState && state.travelUiState.selectedMapFilter || 'activity';
        container.innerHTML = pois.length
            ? `<div class="bg-white rounded-3xl border border-gray-100 shadow-sm p-5 animate-fade-in"><div class="flex items-start justify-between gap-3"><div class="min-w-0 flex-1"><p class="text-[10px] font-bold uppercase tracking-wider text-brand-green">地图概览</p><h3 class="text-lg font-bold text-gray-900 mt-1">${escapeHtml((state.travelPlan && state.travelPlan.brief && state.travelPlan.brief.destination) || '当前行程地图')}</h3></div><button ${dataAttrs('open_map', { filter })} class="px-4 py-2.5 rounded-2xl bg-origami-gradient text-white text-xs font-bold shadow-neon-green">打开原生地图</button></div><div class="space-y-3 mt-4">${pois.slice(0, 8).map((poi) => `<button ${dataAttrs('open_map', { filter, poiId: poi.id || '' })} class="w-full text-left rounded-2xl border border-gray-100 bg-gray-50 px-4 py-3"><p class="text-sm font-semibold text-gray-800">${escapeHtml(poi.name || '点位')}</p><p class="text-[11px] text-gray-500 mt-1">${escapeHtml(poi.address || poi.category || '')}</p></button>`).join('')}</div></div>`
            : renderEmptyState('暂无地图点位', '完成目的地同步或生成行程后，这里会展示真实 POI 数据。', `<button ${dataAttrs('open_map', { filter })} class="px-4 py-2.5 rounded-2xl bg-origami-gradient text-white text-xs font-bold shadow-neon-green">打开原生地图</button>`);
    }

    function applyProfileTab(activeTab) {
        document.querySelectorAll('.profile-tab').forEach((button) => {
            const isActive = button.getAttribute('data-profile-tab') === activeTab;
            button.classList.toggle('active', isActive);
            button.classList.toggle('text-gray-500', !isActive);
            button.classList.toggle('bg-gray-50', !isActive);
            button.classList.toggle('text-white', isActive);
        });
        const historyPanel = document.getElementById('profile-tab-history');
        const favoritesPanel = document.getElementById('profile-tab-favorites');
        if (historyPanel) historyPanel.classList.toggle('hidden', activeTab !== 'history');
        if (favoritesPanel) favoritesPanel.classList.toggle('hidden', activeTab !== 'favorites');
    }

    function renderProfile(state) {
        const overlay = document.getElementById('profile-overlay');
        if (!overlay) return;
        const user = state.user || {};
        const stats = user.stats || [];
        const nameEl = overlay.querySelector('h3.font-bold.text-gray-900.text-base.leading-tight');
        if (nameEl) nameEl.textContent = user.name || '旅行者';
        const statCards = overlay.querySelectorAll('.grid.grid-cols-3 > div');
        stats.slice(0, 3).forEach((stat, index) => {
            const card = statCards[index];
            if (!card) return;
            const lines = card.querySelectorAll('p');
            if (lines[0]) lines[0].textContent = stat.value || '0';
            if (lines[1]) lines[1].textContent = stat.label || '';
        });
        const historyRoot = document.getElementById('profile-tab-history');
        const favoriteRoot = document.getElementById('profile-tab-favorites');
        if (historyRoot) {
            historyRoot.innerHTML = `<div class="space-y-3">${(state.historyConversations || []).map((item) => `<button ${dataAttrs('resume_history_session', { conversationId: item.id || '' })} class="w-full text-left bg-white rounded-2xl border border-gray-100 shadow-sm p-4"><p class="text-sm font-bold text-gray-800">${escapeHtml(item.title || '历史会话')}</p><p class="text-[10px] text-gray-400 mt-1">${escapeHtml(item.subtitle || item.updatedAt || '')}</p><p class="text-[11px] text-gray-500 mt-2">${escapeHtml(item.preview || '')}</p></button>`).join('') || renderEmptyState('暂无历史会话', '保存过的旅行对话会显示在这里。', '')}</div>`;
        }
        if (favoriteRoot) {
            favoriteRoot.innerHTML = `<div class="space-y-3 px-4 py-4">${(state.favoriteItems || []).map((item) => `<button ${dataAttrs('open_favorite_item', { conversationId: item.conversationId || '', nodeId: item.nodeId || '', category: item.category || '' })} class="w-full text-left bg-white rounded-2xl border border-gray-100 shadow-sm p-4"><p class="text-sm font-bold text-gray-800">${escapeHtml(item.title || '收藏项')}</p><p class="text-[10px] text-gray-400 mt-1">${escapeHtml(item.subtitle || item.reason || item.category || '')}</p></button>`).join('') || renderEmptyState('暂无收藏', '收藏的推荐卡片和会话节点会显示在这里。', '')}</div>`;
        }
        applyProfileTab((state.profileUiState && state.profileUiState.activeTab) || 'history');
    }

    function applyHeader(state) {
        const titleEl = document.querySelector('header h1');
        const subtitleEl = document.querySelector('header p');
        const brief = state.travelPlan && state.travelPlan.brief;
        const title = (brief && brief.destination) || state.conversation.title || '智途 TravelHub';
        const subtitle = (state.travelUiState && state.travelUiState.weatherSummary) || (brief && brief.dateRange) || '行程助手已连接';
        if (titleEl) titleEl.textContent = title;
        if (subtitleEl) subtitleEl.innerHTML = '<span class="w-1.5 h-1.5 rounded-full bg-brand-green animate-pulse"></span> ' + escapeHtml(subtitle);
        document.title = '';
    }

    function applyQuickTabs(currentTab, assistantRecommendation) {
        const activeTab = currentTab === 'ai' && assistantRecommendation && assistantRecommendation.category
            ? assistantRecommendation.category
            : currentTab;
        document.querySelectorAll('.quick-tab[data-tab]').forEach((tab) => {
            tab.classList.toggle('active', tab.getAttribute('data-tab') === activeTab);
        });
    }

    function renderMain(state) {
        const container = document.getElementById('chat-container');
        if (!container) return;
        const currentTab = String(state.currentTab || 'home').toLowerCase();
        applyQuickTabs(currentTab, state.assistantRecommendation || null);
        if (currentTab === 'itinerary') {
            renderItinerary(container, state);
        } else if (currentTab === 'map') {
            renderMap(container, state);
        } else {
            renderConversation(container, state);
        }
    }

    function renderApp() {
        const state = getState();
        if (!state) {
            reportRuntime('warn', 'renderApp called without state');
            return;
        }
        stripPreviewArtifacts();
        normalizeStaticLayout();
        if (!state.assistantRecommendation) {
            runtimeUi.recommendationDetail = null;
        }
        applyHeader(state);
        renderMain(state);
        renderProfile(state);
        renderItineraryOverlay(state);
        setOverlayVisible('profile-overlay', runtimeUi.profileOpen);
        setOverlayVisible('itinerary-full-overlay', runtimeUi.itineraryOpen);
        if (window.lucide && typeof window.lucide.createIcons === 'function') {
            window.lucide.createIcons();
        }
        reportRuntime('info', `render ok: tab=${String(state.currentTab || 'home')}`);
    }

    function handleLocalAction(name) {
        if (name === 'open-profile') {
            runtimeUi.profileOpen = true;
            renderApp();
            postAction('open_profile', {});
            return true;
        }
        if (name === 'close-profile') {
            runtimeUi.profileOpen = false;
            renderApp();
            postAction('close_profile', {});
            return true;
        }
        if (name === 'open-itinerary-overlay') {
            runtimeUi.itineraryOpen = true;
            renderApp();
            return true;
        }
        if (name === 'close-itinerary-overlay') {
            runtimeUi.itineraryOpen = false;
            renderApp();
            return true;
        }
        return false;
    }

    function handleDatasetAction(node) {
        const action = node.dataset.zhituAction;
        if (!action) return false;
        const payload = {};
        Object.entries(node.dataset).forEach(([key, value]) => {
            if (key === 'zhituAction' || key === 'zhituLocal') return;
            payload[key] = value;
        });
        if (action === 'switch_tab' && payload.tab) {
            const isRecommendationTab = ['hotel', 'food', 'activity'].includes(String(payload.tab).toLowerCase());
            const state = getState();
            if (state && !isRecommendationTab) {
                state.currentTab = payload.tab;
                renderApp();
            }
        }
        if (action === 'switch_profile_tab' && payload.tab) {
            const state = getState();
            if (state) {
                state.profileUiState = state.profileUiState || {};
                state.profileUiState.activeTab = payload.tab;
                renderApp();
            }
        }
        if (action === 'toggle_recommendation_detail') {
            const nextKey = { category: payload.category || '', itemId: payload.itemId || '' };
            const isSame = runtimeUi.recommendationDetail && runtimeUi.recommendationDetail.category === nextKey.category && runtimeUi.recommendationDetail.itemId === nextKey.itemId;
            runtimeUi.recommendationDetail = isSame ? null : nextKey;
            renderApp();
            return true;
        }
        if (action === 'request_recommendations' || action === 'sort_recommendations') {
            runtimeUi.recommendationDetail = null;
        }
        return postAction(action, payload);
    }

    function submitInput() {
        const input = document.getElementById('chat-input');
        if (!input) {
            reportRuntime('error', 'chat-input not found');
            return;
        }
        const text = (input.value || '').trim();
        if (!text) {
            reportRuntime('warn', 'submit ignored: empty input');
            return;
        }
        input.value = '';
        reportRuntime('info', `submitInput: ${text}`);
        postAction('send_message', { text });
    }

    function bindRuntimeEvents() {
        if (window.__ZHITU_RUNTIME_BOUND__) return;
        window.__ZHITU_RUNTIME_BOUND__ = true;

        document.addEventListener('click', (event) => {
            const actionNode = event.target.closest('[data-zhitu-action]');
            if (actionNode) {
                event.preventDefault();
                event.stopPropagation();
                handleDatasetAction(actionNode);
                return;
            }

            const localNode = event.target.closest('[data-zhitu-local]');
            if (localNode) {
                event.preventDefault();
                event.stopPropagation();
                handleLocalAction(localNode.dataset.zhituLocal);
                return;
            }

            const quickTab = event.target.closest('.quick-tab[data-tab]');
            if (quickTab) {
                event.preventDefault();
                event.stopPropagation();
                const tabValue = quickTab.getAttribute('data-tab') || 'home';
                const action = ['hotel', 'food', 'activity'].includes(tabValue) ? 'request_recommendations' : 'switch_tab';
                handleDatasetAction({
                    dataset: {
                        zhituAction: action,
                        tab: tabValue,
                        category: tabValue,
                    },
                });
                return;
            }

            if (event.target.closest('#send-btn')) {
                event.preventDefault();
                event.stopPropagation();
                submitInput();
                return;
            }

            if (event.target.closest('#header-map-btn')) {
                event.preventDefault();
                event.stopPropagation();
                postAction('open_map', {});
                return;
            }

            if (event.target.closest('#user-avatar-btn')) {
                event.preventDefault();
                event.stopPropagation();
                handleLocalAction('open-profile');
                return;
            }

            if (event.target.closest('#profile-overlay-close')) {
                event.preventDefault();
                event.stopPropagation();
                handleLocalAction('close-profile');
                return;
            }

            const profileTab = event.target.closest('.profile-tab[data-profile-tab]');
            if (profileTab) {
                event.preventDefault();
                event.stopPropagation();
                handleDatasetAction({
                    dataset: {
                        zhituAction: 'switch_profile_tab',
                        tab: profileTab.getAttribute('data-profile-tab') || 'history',
                    },
                });
                return;
            }

            if (event.target.closest('#itinerary-overlay-close')) {
                event.preventDefault();
                event.stopPropagation();
                handleLocalAction('close-itinerary-overlay');
            }
        }, true);

        document.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' && !event.shiftKey && event.target && event.target.id === 'chat-input') {
                event.preventDefault();
                event.stopPropagation();
                submitInput();
            }
        }, true);
    }

    window.openProfileOverlay = function () {
        runtimeUi.profileOpen = true;
        renderApp();
    };

    window.closeProfileOverlay = function () {
        runtimeUi.profileOpen = false;
        renderApp();
    };

    window.openItineraryOverlay = function () {
        runtimeUi.itineraryOpen = true;
        renderApp();
    };

    window.closeItineraryOverlay = function () {
        runtimeUi.itineraryOpen = false;
        renderApp();
    };

    window._dispatchTabByKey = function (tabKey) {
        const action = ['hotel', 'food', 'activity'].includes(String(tabKey).toLowerCase()) ? 'request_recommendations' : 'switch_tab';
        postAction(action, { tab: tabKey, category: tabKey });
    };

    window._handleNavClick = function (item) {
        const tabKey = item && typeof item.getAttribute === 'function' ? item.getAttribute('data-tab') : '';
        if (!tabKey) return;
        const action = ['hotel', 'food', 'activity'].includes(String(tabKey).toLowerCase()) ? 'request_recommendations' : 'switch_tab';
        postAction(action, { tab: tabKey, category: tabKey });
    };

    window.__ZHITU_RECEIVE_STATE__ = function (input) {
        try {
            const nextState = readState(input);
            if (!nextState) {
                reportRuntime('error', 'readState returned null');
                return;
            }
            window.__ZHITU_STATE__ = nextState;
            bindRuntimeEvents();
            renderApp();
        } catch (error) {
            reportRuntime('error', `receive state failed: ${error && error.message ? error.message : String(error)}`);
            throw error;
        }
    };

    stripPreviewArtifacts();
    normalizeStaticLayout();
    bindRuntimeEvents();
    window.onerror = function (message, source, lineno, colno) {
        reportRuntime('error', `${String(message)} @ ${String(lineno || 0)}:${String(colno || 0)}`);
        return false;
    };
})();
