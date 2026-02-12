import Link from 'next/link';
import React from 'react';

export default function Header() {
    return (
        <header className="fixed top-0 w-full z-50 bg-white/80 backdrop-blur-md border-b border-gray-100 dark:bg-black/80 dark:border-white/10 transition-colors duration-300">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex justify-between items-center h-16">
                    {/* Logo / Brand */}
                    <div className="flex-shrink-0 flex items-center">
                        <Link
                            href="/"
                            className="text-2xl font-bold bg-gradient-to-r from-indigo-500 to-purple-600 bg-clip-text text-transparent hover:opacity-80 transition-opacity"
                        >
                            AI Blog
                        </Link>
                    </div>

                    {/* Navigation Links */}
                    <nav className="hidden md:flex space-x-8">
                        <Link
                            href="/"
                            className="text-gray-600 hover:text-indigo-600 dark:text-gray-300 dark:hover:text-indigo-400 font-medium transition-colors"
                        >
                            Home
                        </Link>
                        <Link
                            href="/login"
                            className="text-gray-600 hover:text-indigo-600 dark:text-gray-300 dark:hover:text-indigo-400 font-medium transition-colors"
                        >
                            Login
                        </Link>
                        <Link
                            href="/admin/jobs"
                            className="text-gray-600 hover:text-indigo-600 dark:text-gray-300 dark:hover:text-indigo-400 font-medium transition-colors"
                        >
                            Jobs
                        </Link>


                    </nav>

                    {/* Mobile Menu Placeholder (Optional for now) */}
                    <div className="md:hidden">
                        {/* Simple mobile menu trigger could go here */}
                    </div>
                </div>
            </div>
        </header >
    );
}
