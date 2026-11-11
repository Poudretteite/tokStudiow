import { useId, type ReactNode, type SelectHTMLAttributes } from 'react';

type Props = SelectHTMLAttributes<HTMLSelectElement> & { label: string; children: ReactNode };

export function Select({ label, required, children, ...rest }: Props) {
    const id = useId();
    return (
        <div>
            <label htmlFor={id} className="mb-1 block text-sm font-semibold">
                {label}
                {required && (
                    <span aria-hidden="true" className="text-red-700">
            {' '}*
          </span>
                )}
            </label>
            <select
                id={id}
                required={required}
                {...rest}
                className="w-full rounded-sm border border-line bg-white px-3 py-2 text-[15px] outline-none focus:border-navy focus:ring-2 focus:ring-navy/20"
            >
                {children}
            </select>
        </div>
    );
}