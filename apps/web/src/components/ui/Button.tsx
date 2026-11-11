import type { ButtonHTMLAttributes, Ref } from 'react';
import { Spinner } from './Spinner';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: 'primary' | 'secondary';
    loading?: boolean;
    ref?: Ref<HTMLButtonElement>;
};

const VARIANTS = {
    primary: 'bg-navy text-white hover:bg-navy-dark',
    secondary: 'border border-line bg-white text-ink hover:bg-page',
};

export function Button({ variant = 'primary', loading = false, disabled, className = '', children, ...rest }: Props) {
    return (
        <button
            {...rest}
            disabled={disabled || loading}
            className={`inline-flex items-center justify-center gap-2 rounded-sm px-4 py-2 text-[15px] font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-navy disabled:cursor-not-allowed disabled:opacity-60 ${VARIANTS[variant]} ${className}`}
        >
            {loading && <Spinner size="sm" />}
            {children}
        </button>
    );
}