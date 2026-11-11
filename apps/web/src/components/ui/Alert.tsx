import type { ReactNode } from 'react';

export function Alert({ children }: { children: ReactNode }) {
    return (
        <div role="alert" className="border-l-4 border-red-700 bg-red-50 px-4 py-3 text-sm text-red-900">
            {children}
        </div>
    );
}