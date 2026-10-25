import React from 'react';
import { Plus, Trash2, PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { usePersistentToggle } from '../usePersistentToggle';
import { apiFetch } from '../api';
import { useI18n } from '../i18n';

// Conversation list beside the chat thread (app navigation lives in AppShell's rail).
function Sidebar({ chats, currentChatId, onSelectChat, onNewChat, historyState }) {
    const { t } = useI18n();
    const [collapsed, toggleCollapsed] = usePersistentToggle('gymai.convos.collapsed');

    const handleDeleteChat = async (chat) => {
        if (!window.confirm(t('chat.deleteConfirm', { title: chat.title || t('chat.thisConversation') }))) return;
        try {
            const result = await apiFetch(`/api/v1/chat/${chat.id}`, { method: 'DELETE' });
            if (result.status === 'success') {
                window.location.reload();
            }
        } catch (error) {
            console.error('Delete error:', error);
        }
    };

    const toggle = (
        <button type="button" className="panel-toggle convos-toggle" onClick={toggleCollapsed} aria-expanded={!collapsed}
                title={collapsed ? t('chat.showList') : t('chat.hideList')} aria-label={collapsed ? t('chat.showList') : t('chat.hideList')}>
            {collapsed ? <PanelLeftOpen size={18} aria-hidden="true" className="flip-rtl" />
                       : <PanelLeftClose size={18} aria-hidden="true" className="flip-rtl" />}
        </button>
    );

    if (collapsed) {
        return (
            <aside className="convos is-collapsed" aria-label={t('chat.conversations')}>
                {toggle}
                <button type="button" className="panel-toggle convos-new-mini" onClick={onNewChat} title={t('chat.new')} aria-label={t('chat.new')}>
                    <Plus size={18} aria-hidden="true" />
                </button>
            </aside>
        );
    }

    return (
        <aside className="convos" aria-label={t('chat.conversations')}>
            <div className="convos-head">
                <div className="convos-title-row">
                    <h2 className="convos-title">{t('chat.conversations')}</h2>
                    {toggle}
                </div>
                {/* Narrow screens: the list collapses into this picker (see .convos-pick in index.css). */}
                <label className="convos-pick">
                    <span className="visually-hidden">{t('chat.open')}</span>
                    <select className="input" dir="auto" value={currentChatId ?? ''}
                            onChange={(e) => (e.target.value ? onSelectChat(Number(e.target.value)) : onNewChat())}>
                        <option value="">{t('chat.new')}</option>
                        {chats.map((chat) => <option key={chat.id} value={chat.id}>{chat.title || t('chat.new')}</option>)}
                    </select>
                </label>
                <button type="button" className="btn btn-primary btn-block convos-new" onClick={onNewChat}>
                    <Plus size={18} aria-hidden="true" /> <span className="convos-new-label">{t('chat.new')}</span>
                </button>
            </div>

            <div className="convos-list">
                {historyState === 'loading' && chats.length === 0 ? (
                    <p className="convos-empty">{t('chat.loading')}</p>
                ) : historyState === 'error' ? (
                    <p className="convos-empty is-error">{t('chat.loadError')}</p>
                ) : chats.length === 0 ? (
                    <p className="convos-empty">{t('chat.none')}</p>
                ) : (
                    chats.map(chat => (
                        <div key={chat.id} className={`convo${currentChatId === chat.id ? ' active' : ''}`}>
                            <button type="button" className="convo-open" onClick={() => onSelectChat(chat.id)}
                                    aria-current={currentChatId === chat.id ? 'true' : undefined}>
                                <span className="convo-title" dir="auto">{chat.title || t('chat.new')}</span>
                                <span className="convo-preview" dir="auto">{chat.last_message || t('chat.noMessages')}</span>
                            </button>
                            <button type="button" className="convo-delete" onClick={() => handleDeleteChat(chat)}
                                    aria-label={t('chat.deleteLabel', { title: chat.title || t('chat.thisConversation') })}>
                                <Trash2 size={16} aria-hidden="true" />
                            </button>
                        </div>
                    ))
                )}
            </div>
        </aside>
    );
}

export default Sidebar;
