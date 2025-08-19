# <img src="assets/logo.svg" alt="fuzion logo" width="25" /> Fuzion

[![OpenSSF
Scorecard](https://api.securityscorecards.dev/projects/github.com/tokiwa-software/fuzion/badge)](https://api.securityscorecards.dev/projects/github.com/tokiwa-software/fuzion)
[![run tests on linux](https://github.com/tokiwa-software/fuzion/actions/workflows/linux.yml/badge.svg)](https://github.com/tokiwa-software/fuzion/actions/workflows/linux.yml)
[![run tests on macOS](https://github.com/tokiwa-software/fuzion/actions/workflows/apple.yml/badge.svg)](https://github.com/tokiwa-software/fuzion/actions/workflows/apple.yml)
[![run tests on windows](https://github.com/tokiwa-software/fuzion/actions/workflows/windows.yml/badge.svg)](https://github.com/tokiwa-software/fuzion/actions/workflows/windows.yml)


## A language with a focus on simplicity, safety and correctness.

> Please note that this language is work in progress.

---

<!--ts-->
   * [Examples](#examples)
   * [Documentation](#documentation)
   * [Clone](#clone)
   * [Required packages](#required-packages)
     * [Linux](#linux)
     * [MacOS](#macos)
     * [Windows](#windows)
   * [Build](#build)
   * [Run](#run)
   * [Soft dependencies](#soft-dependencies)
   * [Install prebuilt](#install-prebuilt)
   * [Language server](#language-server)
     * [Install](#install)
       * [Vim](#vim)
       * [Emacs](#emacs)
         * [Eglot](#eglot)
         * [LSP-Mode](#lsp-mode)
     * [Run standalone](#run-standalone)
       * [socket](#transport-socket)
       * [stdio](#transport-stdio)
     <!-- NYI: UNDER DEVELOPMENT: * [Implementation state](#implementation-state) -->
<!--te-->

---

## Examples

```
hello_world is

  # first we define a custom mutate effect.
  # we will need this for buffered reading from stdin
  #
  lm : mutate is

  # calling `lm` creates an instance of our mutate effect,
  # `instate_self` is then used to instate this instance and
  # run code in the context of the instated effect.
  #
  lm ! ()->

    # read someone's name from standard input
    #
    get_name =>
      (io.stdin.reader lm) ! ()->
        (io.buffered lm).read_line ? str String => str | io.end_of_file => ""

    # greet someone with the name given
    #
    greet(name String) is
      say "Hello, {name}!"

    # greet the user
    #
    x := greet get_name

    # you can access any feature - even argument features of other features
    # from outside
    #
    say "How are you, {x.name}?"
```

This `hello_world` example demonstrates one important concept in Fuzion quite
well: Everything is a *feature*. *Features* are Fuzion's response to the mess
that is created by *classes*, *methods*, *interfaces*, and various other
concepts in other programming languages. Since everything is a feature, the
programmer does not need to care and the compiler will do this work. As you can
see, it is even possible to access the argument features of some feature from
outside.

```
ex_gcd is

  # return common divisors of a and b
  #
  common_divisors_of(a, b i32) =>
    max := max a.abs b.abs
    (1..max).flat_map i->
      if (a % i = 0) && (b % i = 0)
        [-i, i]
      else
        []


  # find the greatest common divisor of a and b
  #
  gcd(a, b i32)
    pre
      safety: (a != 0 || b != 0)
    post
      safety: a % result = 0
      safety: b % result = 0
      pedantic: (common_divisors_of a b).reduce bool true (acc,cur -> acc && (result % cur = 0))
  =>
    if b = 0 then a else gcd b (a % b)


  say <| gcd 8 12
  say <| gcd -8 12
  say <| gcd 28 0
```

This example implements a simple variant of an algorithm that finds the greatest
common divisor of two numbers. However, it also demonstrates one of Fuzion's
notable features: design by contract. By specifying pre- and postconditions for
features, correctness checks are made possible.

```
generator_effect is
  # define a generator effect with a yield operation
  #
  gen(T type,
      yield T->unit    # yield is called by code to yield values
      ) : effect is

  # traverse a list and yield the elements
  #
  list.traverse unit =>
    match list.this
      c Cons => (generator_effect.gen A).env.yield c.head; c.tail.traverse
      nil =>

  # bind the yield operation dynamically
  #
  (gen i32 (i -> say "yielded $i")) ! ()->
    [0,8,15].as_list.traverse
```

Another major concept in Fuzion is that of the
*[algebraic effect](https://en.wikipedia.org/wiki/Effect_system)* - a new
approach to encapsulating code with side effects in a safe way.

In the example above, a custom *effect* has been used to implement a generator
with a `yield` operation. In some other languages, this requires a keyword
`yield` to be provided by the language, but in Fuzion this can be implemented
without language support.

If you want to play around with Fuzion, try the
[interactive tutorial](https://fuzion-lang.dev/tutorial/index).

## Documentation

Check [fuzion-lang.dev](https://fuzion-lang.dev) for language and implementation design.


## Clone

> Note that the current directory must not contain any spaces. Make sure you have `git` installed.

    git clone https://github.com/tokiwa-software/fuzion

## Required packages

### Linux

> For Debian based systems this command should install all requirements:
>
>     sudo apt-get install make clang libgc1 libgc-dev openjdk-21-jdk

- OpenJDK 21, e.g. [Adoptium](https://github.com/adoptium/temurin21-binaries/releases/)
- clang LLVM C compiler
- GNU make
- libgc

### MacOS

> This command should install all requirements:
>
>     brew install bdw-gc gnu-sed make temurin llvm
>
> Additionally you may need to update your PATH environment variable e.g.:
>
>     export PATH:"/usr/local/opt/gnu-sed/libexec/gnubin:/usr/local/opt/gnu-make/libexec/gnubin:$PATH"

- OpenJDK 21, e.g. [Adoptium](https://github.com/adoptium/temurin21-binaries/releases/)
- clang LLVM C compiler
- GNU make
- libgc


### Windows

> Note that building from powershell/cmd does not work yet.

1) Install chocolatey: [chocolatey.org](https://chocolatey.org/install)
2) In Powershell:
    1) choco install git openjdk make msys2 diffutils
    2) [Environment]::SetEnvironmentVariable("Path","c:\tools\msys64\ucrt64\bin;" + $env:Path , "User")
3) In file C:\tools\msys64\msys2_shell.cmd change line: 'rem set MSYS2_PATH_TYPE=inherit' to 'set MSYS2_PATH_TYPE=inherit'
4) In msys2 shell (execute C:\tools\msys64\msys2_shell.cmd):
    1) pacman -S mingw-w64-x86_64-clang
    2) make
5) execute ./bin/windows_install_boehm_gc.sh

## Build

> Make sure java/javac and clang are in your $PATH.

    cd fuzion
    make

You should have a folder called **build** now.

## Run

    cd build
    export PATH=$PWD/bin:$PATH
    cd tests/rosettacode_factors_of_an_integer
    fz factors

To compile the same example (requires clang C compiler):

    fz -c factors
    ./factors

Have fun!


## Soft dependencies

The compiler can be built and used without these dependencies.

But the following tools/dependencies are used e.g. for generating the documentation or for running the test suite:

- antlr, for the ebnf grammar
- asciidoctor, asciidoctor-pdf
- sed, for normalizing test output
- wget, for downloading jar dependencies
- org.eclipse.lsp4j and others, for the language server


## Install prebuilt

[![Packaging status](https://repology.org/badge/vertical-allrepos/fuzion.svg)](https://repology.org/project/fuzion/versions)


## Language Server

### Install
|Client|Repository|
|---|---|
|vscode|https://github.com/tokiwa-software/vscode-fuzion|
|vim|see instructions below|
|emacs|see instructions below|
|eclipse (theia)|https://github.com/tokiwa-software/vscode-fuzion|

#### Vim

0) Note: fuzion_language_server (from ./bin/) needs to be in $PATH

1) Example .vimrc:
    ```vim
    :filetype on

    call plug#begin('~/.vim/plugged')
    Plug 'neoclide/coc.nvim', {'branch': 'release'}
    call plug#end()
    ```
2) in vim

    1) `:PlugInstall`

    2) `:CocConfig`

          ```json
          {
            "languageserver": {
              "fuzion": {
                "command": "fuzion_language_server",
                "args" : ["-stdio"],
                "filetypes": [
                  "fz",
                  "fuzion"
                ]
              }
            }
          }
          ```

3) add filetype-detection file ~/.vim/ftdetect/fz.vim
    ```vim
    au BufRead,BufNewFile *.fz            set filetype=fz
    ```

#### Emacs

> fuzion_language_server (from ./bin/) needs to be in $PATH

For emacs there is two options eglot or lsp-mode.

##### Eglot


- M-x package-install RET eglot RET (only needed for emacs version <29)
- add the following code to ~/.emacs.d/fuzion-lsp.el to enable [eglot](https://github.com/joaotavora/eglot)


```elisp
(require 'package)
(add-to-list 'package-archives '("melpa" . "https://melpa.org/packages/") t)
(add-to-list 'package-archives '("elpa" . "https://elpa.gnu.org/packages/"))
;; Comment/uncomment this line to enable MELPA Stable if desired.  See `package-archive-priorities`
;; and `package-pinned-packages`. Most users will not need or want to do this.
;;(add-to-list 'package-archives '("melpa-stable" . "https://stable.melpa.org/packages/") t)
(package-initialize)
(custom-set-variables
 ;; custom-set-variables was added by Custom.
 ;; If you edit it by hand, you could mess it up, so be careful.
 ;; Your init file should contain only one such instance.
 ;; If there is more than one, they won't work right.
 '(inhibit-startup-screen t)
 '(package-selected-packages '(eglot ##)))
(custom-set-faces
 ;; custom-set-faces was added by Custom.
 ;; If you edit it by hand, you could mess it up, so be careful.
 ;; Your init file should contain only one such instance.
 ;; If there is more than one, they won't work right.
 )

(define-derived-mode fuzion-mode
  fundamental-mode "Fuzion"
  "Major mode for Fuzion.")

(add-to-list 'auto-mode-alist '("\\.fz\\'" . fuzion-mode))

(require 'eglot)

(add-to-list 'eglot-server-programs
             '(fuzion-mode . ("fuzion_language_server" "-stdio")))

(add-hook 'after-init-hook 'global-company-mode)
(add-hook 'fuzion-mode-hook 'eglot-ensure)

(provide 'init)
;;; init.el ends here
```

- add following line to ~/.emacs.d/init.el or to ~/.emacs

  (load "~/.emacs.d/fuzion-lsp.el")

##### LSP-Mode

- install lsp-mode, flycheck and company for emacs using
    - M-x package-install RET lsp-mode
    - M-x package-install RET flycheck
    - M-x package-install RET company RET
- add the following code to ~/.emacs.d/fuzion-lsp.el to enable [lsp-mode](https://github.com/emacs-lsp/lsp-mode)

```elisp
(require 'package)
(add-to-list 'package-archives '("melpa" . "https://melpa.org/packages/") t)
(add-to-list 'package-archives '("elpa" . "https://elpa.gnu.org/packages/"))
(package-initialize)
(custom-set-variables
 ;; custom-set-variables was added by Custom.
 ;; If you edit it by hand, you could mess it up, so be careful.
 ;; Your init file should contain only one such instance.
 ;; If there is more than one, they won't work right.
 '(inhibit-startup-screen t)
 '(package-selected-packages '(lsp-ui company flycheck lsp-mode ##)))
(custom-set-faces
 ;; custom-set-faces was added by Custom.
 ;; If you edit it by hand, you could mess it up, so be careful.
 ;; Your init file should contain only one such instance.
 ;; If there is more than one, they won't work right.
 )

(define-derived-mode fuzion-mode
  fundamental-mode "Fuzion"
  "Major mode for Fuzion.")

(add-to-list 'auto-mode-alist '("\\.fz\\'" . fuzion-mode))

(require 'lsp-mode)
(global-flycheck-mode)
(add-to-list 'lsp-language-id-configuration '(fuzion-mode . "fuzion"))

(defgroup lsp-fuzionlsp nil
  "LSP support for Fuzion, using fuzionlsp."
  :group 'lsp-mode
  :link '(url-link ""))

(lsp-register-client
 (make-lsp-client :new-connection (lsp-stdio-connection  (lambda ()
                                                          `(,"fuzion_language_server"
                                                            "-stdio")))
                  :major-modes '(fuzion-mode)
                  :priority -1
                  :server-id 'fuzionls))


(lsp-consistency-check lsp-fuzion)

(add-hook 'fuzion-mode-hook #'lsp)
(add-hook 'after-init-hook 'global-company-mode)

(setq lsp-enable-symbol-highlighting t)

;; (setq  lsp-enable-semantic-highlighting t
;;        lsp-semantic-tokens-enable t
;;        lsp-semantic-tokens-warn-on-missing-face t
;;        lsp-semantic-tokens-apply-modifiers nil
;;        lsp-semantic-tokens-allow-delta-requests nil
;;        lsp-semantic-tokens-allow-ranged-requests nil)

;; (setq lsp-modeline-code-actions-mode t)

;; (setq lsp-modeline-code-actions-segments '(name icon))

;; (setq lsp-log-io t)

(provide 'lsp-fuzion)

(provide 'init)
;;; init.el ends here
```

- add following line to ~/.emacs.d/init.el or to ~/.emacs

  (load "~/.emacs.d/fuzion-lsp.el")

### Run standalone

#### Transport socket
- run `./bin/fuzion_language_server -socket --port=3000`
- connect the client to the (random) port the server prints to stdout.

#### Transport stdio
- run `./bin/fuzion_language_server -stdio`

<!--
### Implementation state

|Feature|Status|
|---|---|
|diagnostics|☑|
|completion|☑|
|hover|☑|
|signatureHelp|☑|
|declaration|☐|
|definition|☑|
|typeDefinition|☐|
|implementation|☐|
|references|☑|
|documentHighlight|☐|
|documentSymbol|☑|
|codeAction|☐|
|codeLens|☐|
|documentLink|☐|
|documentColor|☐|
|colorPresentation|☐|
|formatting|☐|
|rangeFormatting|☐|
|onTypeFormatting|☐|
|rename|☑|
|prepareRename|☑|
|foldingRange|☐|
|selectionRange|☐|
|prepareCallHierarchy|☐|
|callHierarchy incoming|☐|
|callHierarchy outgoing|☐|
|semantic tokens|☑|
|linkedEditingRange|☐|
|moniker|☐|
|inlayHints|☐|
|inlineValue|☐|
|type hierarchy|☐|
|notebook document support|☐| -->
