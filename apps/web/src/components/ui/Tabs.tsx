import { useId, useState, type KeyboardEvent, type ReactNode } from 'react';

interface Tab {
    id: string;
    label: string;
    content: ReactNode;
}

/** Dostępne zakładki: obsługa strzałkami i role ARIA zgodne z wzorcem WAI-ARIA Tabs. */
export function Tabs({ tabs, label }: { tabs: Tab[]; label: string }) {
    const [active, setActive] = useState(tabs[0].id);
    const baseId = useId();
    const tabId = (id: string) => `${baseId}-tab-${id}`;
    const panelId = (id: string) => `${baseId}-panel-${id}`;

    function handleKeyDown(event: KeyboardEvent) {
        if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') {
            return;
        }
        const index = tabs.findIndex((t) => t.id === active);
        const step = event.key === 'ArrowRight' ? 1 : -1;
        const next = tabs[(index + step + tabs.length) % tabs.length];
        setActive(next.id);
        document.getElementById(tabId(next.id))?.focus();
    }

    const current = tabs.find((t) => t.id === active)!;

    return (
        <div>
            <div role="tablist" aria-label={label} className="flex gap-1 border-b border-line" onKeyDown={handleKeyDown}>
                {tabs.map((tab) => {
                    const selected = tab.id === active;
                    return (
                        <button
                            key={tab.id}
                            id={tabId(tab.id)}
                            type="button"
                            role="tab"
                            aria-selected={selected}
                            aria-controls={panelId(tab.id)}
                            tabIndex={selected ? 0 : -1}
                            onClick={() => setActive(tab.id)}
                            className={`-mb-px border-b-[3px] px-4 pt-2.5 pb-2 text-[15px] font-semibold focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-navy ${
                                selected ? 'border-navy text-navy' : 'border-transparent text-muted hover:text-ink'
                            }`}
                        >
                            {tab.label}
                        </button>
                    );
                })}
            </div>
            <div role="tabpanel" id={panelId(current.id)} aria-labelledby={tabId(current.id)} className="pt-4">
                {current.content}
            </div>
        </div>
    );
}