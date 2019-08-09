import setuptools

VERSION = "0.1"

install_requires = [
    "bottle",
    "requests",
    "pyyaml"
]

setuptools.setup(
    name="playwell-service-container",
    version=VERSION,
    author="Chihz",
    author_email="chihongze@gmail.com",
    description="The playwell python service container",
    packages=setuptools.find_packages("."),
    install_requires=install_requires,
    entry_points={
        "console_scripts": [
            "playwell_service = playwell.service.launcher:main",
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
