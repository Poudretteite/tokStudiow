import type { ReactNode } from 'react';

interface Props {
    title: ReactNode;
    aside?: ReactNode;
    flush?: boolean;
    children: ReactNode;
}

export function Panel({ title, aside, flush = false, children }: Props) {
    return (
        <section className="border border-line bg-white">
            <header className="flex items-center justify-between gap-3 border-b border-line bg-panel-head px-4 py-2.5">
                <h2 className="text-base font-semibold">{title}</h2>
                {aside}
            </header>
            <div className={flush ? '' : 'p-4'}>{children}</div>
        </section>
    );
}