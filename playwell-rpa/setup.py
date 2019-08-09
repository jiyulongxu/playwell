import setuptools

VERSION = "0.1"

install_requires = [
    "selenium",
    "bs4",
    "openpyxl",
    "pymysql",
]

setuptools.setup(
    name="playwell-rpa",
    version=VERSION,
    author="Chihz",
    author_email="chihongze@gmail.com",
    description="Playwell rpa",
    packages=setuptools.find_packages("."),
    install_requires=install_requires,
    entry_points={
        "console_scripts": [

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
