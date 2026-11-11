import type { ReactNode } from 'react';

const VARIANTS = {
    error: 'border-red-700 bg-red-50 text-red-900',
    success: 'border-green-700 bg-green-50 text-green-900',
};

export function Alert({ children, variant = 'error' }: { children: ReactNode; variant?: keyof typeof VARIANTS }) {
    return (
        <div role={variant === 'error' ? 'alert' : 'status'} className={`border-l-4 px-4 py-3 text-sm ${VARIANTS[variant]}`}>
            {children}
        </div>
    );
}