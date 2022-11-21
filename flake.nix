{
  description = "The Fuzion Language Implementation";

  inputs = {
    nixpkgs.url = "nixpkgs/nixos-22.05";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = inputs @ { self, nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in rec {
        packages = flake-utils.lib.flattenTree rec {
          fuzion = pkgs.stdenv.mkDerivation {
            pname = "fuzion";
            version = "${nixpkgs.lib.fileContents ./version.txt}-${nixpkgs.lib.substring 0 8 self.lastModifiedDate}-${self.shortRev or "dirty"}";

            src = nixpkgs.lib.cleanSource ./.;

            nativeBuildInputs = with pkgs; [
              antlr
              jdk
              makeWrapper
              pcre
            ];

            makeFlags = [ "FUZION_BIN_BASH=${pkgs.bash}/bin/bash" ];

            # NOTE patchShebangs only patches executable files
            patchPhase = ''
              patchShebangs bin/ebnf.sh
              chmod +x bin/fz
              patchShebangs bin/fz
              chmod +x bin/fzjava
              patchShebangs bin/fzjava
            '';

            installPhase = ''
              cp -r build $out
            '';

            postFixup = ''
              wrapProgram $out/bin/fz \
                --set FUZION_JAVA ${pkgs.jdk.home}/bin/java \
                --prefix PATH : ${pkgs.lib.makeBinPath [ pkgs.clang_10 ]} \
                --prefix CPATH : ${pkgs.lib.getDev pkgs.boehmgc}/include \
                --prefix LIBRARY_PATH : ${pkgs.lib.getLib pkgs.boehmgc}/lib
              wrapProgram $out/bin/fzjava \
                --set FUZION_JAVA ${pkgs.jdk.home}/bin/java \
                --prefix PATH : ${pkgs.lib.makeBinPath [ pkgs.clang_10 ]} \
                --prefix CPATH : ${pkgs.lib.getDev pkgs.boehmgc}/include \
                --prefix LIBRARY_PATH : ${pkgs.lib.getLib pkgs.boehmgc}/lib
            '';
          };

          default = fuzion;
        };

        devShell = pkgs.mkShell {
          buildInputs = with pkgs; [
            antlr
            clang_10
            jdk
            pcre
          ];

          shellHook = ''
            export PATH="$PWD/build/bin:$PATH"
            export MAKEFLAGS="FUZION_BIN_BASH=${pkgs.bash}/bin/bash"
          '';
        };
      });
}
