import { Link } from 'react-router';

interface Crumb {
    label: string;
    to?: string;
}

/** Ścieżka nawigacji i tytuł strony. */
export function PageHeader({ title, crumbs = [] }: { title: string; crumbs?: Crumb[] }) {
    const trail: Crumb[] = [{ label: 'Strona główna', to: '/' }, ...crumbs];
    return (
        <div className="mb-6">
            {crumbs.length > 0 && (
                <nav aria-label="Ścieżka nawigacji" className="mb-1">
                    <ol className="flex flex-wrap items-center gap-1.5 text-sm text-muted">
                        {trail.map((crumb, i) => {
                            const last = i === trail.length - 1;
                            return (
                                <li key={crumb.label} className="flex items-center gap-1.5">
                                    {crumb.to && !last ? (
                                        <Link to={crumb.to} className="text-navy underline-offset-2 hover:underline">
                                            {crumb.label}
                                        </Link>
                                    ) : (
                                        <span aria-current={last ? 'page' : undefined}>{crumb.label}</span>
                                    )}
                                    {!last && <span aria-hidden="true">›</span>}
                                </li>
                            );
                        })}
                    </ol>
                </nav>
            )}
            <h1 className="text-[1.75rem] leading-tight font-semibold">{title}</h1>
        </div>
    );
}