"""Playwell Client Launcher
"""
import sys
import inspect
import argparse

from playwell import (
    init_client,
    API
)


def main():
    init_client()

    # all api modules
    from playwell import (
        definition,
        activity,
        activity_runner,
        thread,
        clock,
        domain,
        message_bus,
        service,
        service_runner,
        slots,
        system
    )

    all_locals = locals()
    modules = [all_locals[var_name] for var_name in all_locals 
        if inspect.ismodule(all_locals[var_name])]

    module_name, api_name, exec_args = _check_args(sys.argv, modules)

    # get module
    if module_name not in all_locals:
        print("The module %s is not exist" % module_name)
        exit(1)
    module = all_locals[module_name]
    if not inspect.ismodule(module):
        print("The %s is not a module" % module_name)
        exit(1)

    if api_name == "help":
        _show_help(module)
        exit(0)

    if not hasattr(module, api_name.upper()):
        print("The api %s is not exist" % api_name)
        exit(1)
    api = getattr(module, api_name.upper())

    parser = argparse.ArgumentParser(description='Call playwell API')
    for arg_declare in api.args_declare:
        parser.add_argument("--%s" % arg_declare.name, **arg_declare.meta)
    args = parser.parse_args(exec_args)
    api.execute(args.__dict__)

def _check_args(args, modules):
    if len(args) < 3:
        print("Invalid command arguments, eg.")
        print("  playwell definition validate --codec yaml --file ./definition.yml")
        print("  playwell activity create --definition test --display_name 'Test activity' --config '{}'")
        print("  playwell activity pause --id 1")
        print()
        print("All modules:")
        for module in modules:
            print("  ", module.__name__[9:], " - ", module.__doc__.strip())
        exit(1)
    return args[1], args[2], args[3:]

def _show_help(module):
    for element_name in dir(module):
        element = getattr(module, element_name)
        if not isinstance(element, API):
            continue
        print("%s: [%s] %s" % (element_name.lower(), element.method, element.url))
        if element.args_declare:
            for arg in element.args_declare:
                print("  --%s %s" % (arg.name, arg.meta))
        else:
            print("  No need arguments")
        print()

if __name__ == "__main__":
    main()
