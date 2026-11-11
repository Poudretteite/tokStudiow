import type { ReactNode } from 'react';
import { Alert } from './ui/Alert';
import { Spinner } from './ui/Spinner';

interface Props<T> {
    query: { isPending: boolean; error: Error | null; data: T | undefined };
    children: (data: T) => ReactNode;
}

export function QueryState<T>({ query, children }: Props<T>) {
    if (query.isPending) {
        return (
            <div className="flex justify-center p-8 text-navy">
                <Spinner />
            </div>
        );
    }
    if (query.error || query.data === undefined) {
        return <Alert>Nie udało się wczytać danych. {query.error?.message}</Alert>;
    }
    return <>{children(query.data)}</>;
}