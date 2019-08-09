import setuptools

VERSION = "0.1"

install_requires = [
    "requests",
    "termcolor",
]

setuptools.setup(
    name="Playwell-Client",
    version=VERSION,
    author="Chihz",
    author_email="chihongze@gmail.com",
    description="The playwell API client",
    packages=setuptools.find_packages("."),
    install_requires=install_requires,
    entry_points={
        "console_scripts": [
            "playwell = playwell.playwell:main",
        ]
    },
    classifiers=[
        'Development Status :: 3 - Alpha',
        'Environment :: Console',
        'Intended Audience :: Developers',
        'License :: OSI Approved :: Apache License',
        'Operating System :: POSIX',
        'Programming Language :: Python',
    ]
)
